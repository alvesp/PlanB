package pt.karate.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * Publica no Kafka os eventos pendentes da outbox.
 *
 * <h2>Semântica de entrega</h2>
 * At-least-once. Um evento só é marcado como publicado depois de o broker confirmar
 * ({@code acks=all}). Se o processo morrer entre a confirmação e o commit da marcação,
 * o evento volta a ser enviado. O read-side tolera duplicados e eventos fora de ordem
 * porque indexa snapshots com versionamento externo ({@code aggregate-version}).
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int tamanhoLote;
    private final Duration timeoutEnvio;
    private final Duration retencao;

    public OutboxRelay(OutboxRepository repository,
                       KafkaTemplate<String, String> kafka,
                       TransactionTemplate tx,
                       Clock clock,
                       OutboxProperties props) {
        this.repository = repository;
        this.kafka = kafka;
        this.tx = tx;
        this.clock = clock;
        this.tamanhoLote = props.tamanhoLote();
        this.timeoutEnvio = props.timeoutEnvio();
        this.retencao = props.retencao();
    }

    @Scheduled(fixedDelayString = "${outbox.intervalo-ms:500}")
    public void publicarPendentes() {
        // Repete enquanto houver lotes cheios, para escoar picos sem esperar pelo próximo ciclo.
        int publicados;
        do {
            Integer n = tx.execute(status -> publicarLote());
            publicados = n == null ? 0 : n;
        } while (publicados == tamanhoLote);
    }

    private int publicarLote() {
        List<OutboxEvento> lote = repository.reservarPendentes(tamanhoLote);
        if (lote.isEmpty()) {
            return 0;
        }

        // Envia o lote todo e só depois espera pelas confirmações (o produtor agrupa os envios).
        List<CompletableFuture<SendResult<String, String>>> envios = new ArrayList<>(lote.size());
        for (OutboxEvento evento : lote) {
            envios.add(kafka.send(paraRecord(evento)));
        }

        long limite = System.nanoTime() + timeoutEnvio.toNanos();
        int publicados = 0;
        for (int i = 0; i < lote.size(); i++) {
            OutboxEvento evento = lote.get(i);
            try {
                long restante = Math.max(0, limite - System.nanoTime());
                envios.get(i).get(restante, TimeUnit.NANOSECONDS);
                evento.marcarPublicado(Instant.now(clock));
                publicados++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                evento.registarFalha("interrompido");
                break;
            } catch (ExecutionException | TimeoutException e) {
                // Um timeout não prova que a mensagem não chegou ao broker: o evento fica
                // pendente e será reenviado (duplicado inofensivo para o read-side).
                Throwable causa = e instanceof ExecutionException ? e.getCause() : e;
                evento.registarFalha(causa.toString());
                log.warn("Falha ao publicar evento {} ({} tentativas): {}",
                        evento.getId(), evento.getTentativas(), causa.toString());
            }
        }
        return publicados;
    }

    private ProducerRecord<String, String> paraRecord(OutboxEvento evento) {
        ProducerRecord<String, String> rec = new ProducerRecord<>(
                evento.getTopic(), evento.getAggregateId().toString(), evento.getPayload());
        rec.headers()
                .add(OutboxHeaders.EVENT_ID, bytes(evento.getId().toString()))
                .add(OutboxHeaders.EVENT_TYPE, bytes(evento.getEventType()))
                .add(OutboxHeaders.AGGREGATE_VERSION, bytes(Long.toString(evento.getAggregateVersion())));
        return rec;
    }

    @Scheduled(cron = "${outbox.limpeza-cron:0 30 3 * * *}")
    public void limparPublicados() {
        Integer apagados = tx.execute(status ->
                repository.apagarPublicadosAntesDe(Instant.now(clock).minus(retencao)));
        log.info("Outbox: {} eventos publicados removidos", apagados);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
