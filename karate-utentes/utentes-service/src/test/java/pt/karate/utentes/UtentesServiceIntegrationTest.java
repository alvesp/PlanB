package pt.karate.utentes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import pt.karate.outbox.OutboxHeaders;
import pt.karate.utentes.events.Topicos;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluxo completo do write-side: REST → Postgres + outbox → relay → Kafka.
 * Requer Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UtentesServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:4.1.0");

    @Autowired
    TestRestTemplate http;

    @Autowired
    ObjectMapper json;

    @Test
    void inscricaoDeMenorPublicaSnapshotComResponsavelFiscal() throws Exception {
        String maeId = criarPessoa(Map.of(
                "nome", "Ana Silva",
                "dataNascimento", LocalDate.now().minusYears(38).toString(),
                "dadosFiscais", Map.of("nif", "123456789", "nomeFaturacao", "Ana Silva", "moradaFiscal", "Rua A, Porto")));
        String criancaId = criarPessoa(Map.of(
                "nome", "Rita Silva",
                "dataNascimento", LocalDate.now().minusYears(9).toString()));

        ResponseEntity<JsonNode> inscricao = http.postForEntity("/api/alunos", Map.of(
                "pessoaId", criancaId,
                "responsaveis", List.of(Map.of(
                        "pessoaId", maeId,
                        "papeis", Map.of("parentesco", "MAE", "responsavelLegal", true,
                                "responsavelFiscal", true, "contactoEmergencia", true, "autorizadoRecolha", true)))),
                JsonNode.class);
        assertThat(inscricao.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String alunoId = inscricao.getBody().get("id").asText();

        ResponseEntity<JsonNode> fiscal = http.getForEntity("/api/alunos/" + alunoId + "/responsavel-fiscal", JsonNode.class);
        assertThat(fiscal.getBody().get("nif").asText()).isEqualTo("123456789");

        ConsumerRecord<String, String> evento = esperarEvento(Topicos.ALUNOS, alunoId);
        JsonNode snapshot = json.readTree(evento.value());
        assertThat(snapshot.get("versao").asLong()).isEqualTo(1);
        assertThat(snapshot.at("/responsavelFiscal/pessoaId").asText()).isEqualTo(maeId);
        assertThat(new String(evento.headers().lastHeader(OutboxHeaders.EVENT_TYPE).value())).isEqualTo("AlunoInscrito");
    }

    @Test
    void menorSemResponsavelFiscalERejeitadoCom422() {
        String paiId = criarPessoa(Map.of(
                "nome", "Rui Costa",
                "dataNascimento", LocalDate.now().minusYears(40).toString()));
        String criancaId = criarPessoa(Map.of(
                "nome", "Tiago Costa",
                "dataNascimento", LocalDate.now().minusYears(8).toString()));

        ResponseEntity<JsonNode> resp = http.postForEntity("/api/alunos", Map.of(
                "pessoaId", criancaId,
                "responsaveis", List.of(Map.of(
                        "pessoaId", paiId,
                        "papeis", Map.of("parentesco", "PAI", "responsavelLegal", true, "responsavelFiscal", true)))),
                JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resp.getBody().get("codigo").asText()).isEqualTo("RESPONSAVEL_FISCAL_SEM_DADOS");
    }

    private String criarPessoa(Map<String, Object> corpo) {
        ResponseEntity<JsonNode> resp = http.postForEntity("/api/pessoas", corpo, JsonNode.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resp.getBody().get("id").asText();
    }

    private ConsumerRecord<String, String> esperarEvento(String topico, String chave) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-" + System.nanoTime());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (var consumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer())) {
            consumer.subscribe(List.of(topico));
            long limite = System.currentTimeMillis() + 15_000;
            List<String> vistos = new ArrayList<>();
            while (System.currentTimeMillis() < limite) {
                for (ConsumerRecord<String, String> rec : consumer.poll(Duration.ofMillis(500))) {
                    if (chave.equals(rec.key())) {
                        return rec;
                    }
                    vistos.add(rec.key());
                }
            }
            throw new AssertionError("Evento para " + chave + " não chegou a " + topico + "; vistos: " + vistos);
        }
    }
}
