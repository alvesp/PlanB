package pt.karate.utentes.consulta.projecao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pt.karate.utentes.consulta.indices.Indices;
import pt.karate.utentes.events.AlunoSnapshot;
import pt.karate.utentes.events.PessoaSnapshot;
import pt.karate.utentes.events.Topicos;

/**
 * Consome os snapshots publicados pelo utentes-service e projeta-os no OpenSearch.
 * O offset só é confirmado depois de a escrita no OpenSearch terminar (AckMode RECORD).
 */
@Component
public class ConsumidorEventos {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorEventos.class);

    private final Projecao projecao;
    private final ObjectMapper objectMapper;

    public ConsumidorEventos(Projecao projecao, ObjectMapper objectMapper) {
        this.projecao = projecao;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topicos.ALUNOS, groupId = "${utentes.consulta.grupo:utentes-query}")
    public void aluno(ConsumerRecord<String, String> rec) throws JsonProcessingException {
        if (rec.value() == null) {
            projecao.remover(Indices.ALUNOS, rec.key());
            return;
        }
        AlunoSnapshot snapshot = objectMapper.readValue(rec.value(), AlunoSnapshot.class);
        boolean escrito = projecao.indexar(Indices.ALUNOS, snapshot.alunoId().toString(), snapshot.versao(), snapshot);
        log.debug("Aluno {} v{} {}", snapshot.alunoId(), snapshot.versao(), escrito ? "projetado" : "ignorado");
    }

    @KafkaListener(topics = Topicos.PESSOAS, groupId = "${utentes.consulta.grupo:utentes-query}")
    public void pessoa(ConsumerRecord<String, String> rec) throws JsonProcessingException {
        if (rec.value() == null) {
            projecao.remover(Indices.PESSOAS, rec.key());
            return;
        }
        PessoaSnapshot snapshot = objectMapper.readValue(rec.value(), PessoaSnapshot.class);
        boolean escrito = projecao.indexar(Indices.PESSOAS, snapshot.pessoaId().toString(), snapshot.versao(), snapshot);
        log.debug("Pessoa {} v{} {}", snapshot.pessoaId(), snapshot.versao(), escrito ? "projetada" : "ignorada");
    }
}
