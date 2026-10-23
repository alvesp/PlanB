package pt.karate.utentes.consulta.projecao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pt.karate.aprendizagem.events.AulaSnapshot;
import pt.karate.aprendizagem.events.GraduacaoCatalogoSnapshot;
import pt.karate.aprendizagem.events.ProgressoAlunoSnapshot;
import pt.karate.aprendizagem.events.TemaSnapshot;
import pt.karate.aprendizagem.events.TopicosAprendizagem;
import pt.karate.aprendizagem.events.TurmaSnapshot;
import pt.karate.utentes.consulta.indices.Indices;
import pt.karate.utentes.events.AlunoSnapshot;
import pt.karate.utentes.events.PessoaSnapshot;
import pt.karate.utentes.events.Topicos;

import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * Projeções do CQRS: consome os snapshots publicados pelos serviços de escrita e
 * mantém um índice OpenSearch por tipo. O offset só é confirmado depois de a escrita no
 * OpenSearch terminar (AckMode RECORD).
 */
@Component
public class ConsumidorEventos {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorEventos.class);
    private static final String GRUPO = "${utentes.consulta.grupo:utentes-query}";

    private final Projecao projecao;
    private final ObjectMapper objectMapper;

    public ConsumidorEventos(Projecao projecao, ObjectMapper objectMapper) {
        this.projecao = projecao;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topicos.ALUNOS, groupId = GRUPO)
    public void aluno(ConsumerRecord<String, String> rec) throws JsonProcessingException {
        projetar(rec, Indices.ALUNOS, AlunoSnapshot.class, s -> s.alunoId().toString(), AlunoSnapshot::versao);
    }

    @KafkaListener(topics = Topicos.PESSOAS, groupId = GRUPO)
    public void pessoa(ConsumerRecord<String, String> rec) throws JsonProcessingException {
        projetar(rec, Indices.PESSOAS, PessoaSnapshot.class, s -> s.pessoaId().toString(), PessoaSnapshot::versao);
    }

    @KafkaListener(topics = TopicosAprendizagem.PROGRESSO, groupId = GRUPO)
    public void progresso(ConsumerRecord<String, String> rec) throws JsonProcessingException {
        projetar(rec, Indices.PROGRESSO, ProgressoAlunoSnapshot.class, s -> s.alunoId().toString(),
                ProgressoAlunoSnapshot::versao);
    }

    @KafkaListener(topics = TopicosAprendizagem.CATALOGO_GRADUACOES, groupId = GRUPO)
    public void graduacao(ConsumerRecord<String, String> rec) throws JsonProcessingException {
        // A chave Kafka é derivada do código; o documento usa o próprio código como id.
        projetar(rec, Indices.GRADUACOES, GraduacaoCatalogoSnapshot.class, GraduacaoCatalogoSnapshot::codigo,
                GraduacaoCatalogoSnapshot::versao);
    }

    @KafkaListener(topics = TopicosAprendizagem.TEMAS, groupId = GRUPO)
    public void tema(ConsumerRecord<String, String> rec) throws JsonProcessingException {
        projetar(rec, Indices.TEMAS, TemaSnapshot.class, s -> s.temaId().toString(), TemaSnapshot::versao);
    }

    @KafkaListener(topics = TopicosAprendizagem.TURMAS, groupId = GRUPO)
    public void turma(ConsumerRecord<String, String> rec) throws JsonProcessingException {
        projetar(rec, Indices.TURMAS, TurmaSnapshot.class, s -> s.turmaId().toString(), TurmaSnapshot::versao);
    }

    @KafkaListener(topics = TopicosAprendizagem.AULAS, groupId = GRUPO)
    public void aula(ConsumerRecord<String, String> rec) throws JsonProcessingException {
        projetar(rec, Indices.AULAS, AulaSnapshot.class, s -> s.aulaId().toString(), AulaSnapshot::versao);
    }

    private <T> void projetar(ConsumerRecord<String, String> rec, String indice, Class<T> tipo,
                              Function<T, String> id, ToLongFunction<T> versao) throws JsonProcessingException {
        if (rec.value() == null) {
            // Tombstone. Nas graduações a chave não é o código, por isso o apagamento só funciona
            // para os restantes tipos (as graduações desativam-se, não se apagam).
            projecao.remover(indice, rec.key());
            return;
        }
        T snapshot = objectMapper.readValue(rec.value(), tipo);
        boolean escrito = projecao.indexar(indice, id.apply(snapshot), versao.applyAsLong(snapshot), snapshot);
        log.debug("{} {} v{} {}", indice, id.apply(snapshot), versao.applyAsLong(snapshot),
                escrito ? "projetado" : "ignorado");
    }
}
