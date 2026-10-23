package pt.karate.aprendizagem.replica;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.karate.aprendizagem.app.EventosAprendizagem;
import pt.karate.aprendizagem.repo.AlunoRefRepository;
import pt.karate.utentes.events.AlunoSnapshot;
import pt.karate.utentes.events.Topicos;

/**
 * Mantém a réplica local dos alunos a partir do tópico compactado do utentes-service.
 * Um grupo novo lê o tópico desde o início e reconstrói a réplica completa.
 * Idempotente: versões repetidas ou antigas são ignoradas pelo upsert.
 */
@Component
public class ReplicaAlunos {

    private static final Logger log = LoggerFactory.getLogger(ReplicaAlunos.class);

    private final AlunoRefRepository alunos;
    private final EventosAprendizagem eventos;
    private final ObjectMapper objectMapper;

    public ReplicaAlunos(AlunoRefRepository alunos, EventosAprendizagem eventos, ObjectMapper objectMapper) {
        this.alunos = alunos;
        this.eventos = eventos;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = Topicos.ALUNOS, groupId = "${aprendizagem.kafka.grupo:aprendizagem-service}")
    @Transactional
    public void aluno(ConsumerRecord<String, String> rec) throws JsonProcessingException {
        if (rec.value() == null) {
            // Tombstone: os registos de aprendizagem referem o aluno, por isso a réplica mantém-se.
            log.info("Tombstone para aluno {} ignorado", rec.key());
            return;
        }
        AlunoSnapshot a = objectMapper.readValue(rec.value(), AlunoSnapshot.class);
        int escritos = alunos.upsert(a.alunoId(), a.nome(), a.dataNascimento(), a.dataInscricao(), a.estado(), a.versao());
        if (escritos == 1) {
            // Aluno novo (ou com nome/estado/inscrição alterados): o read-side passa a ter o seu progresso.
            eventos.progressoAlterado(a.alunoId());
        }
        log.debug("Aluno {} v{} {}", a.alunoId(), a.versao(), escritos == 1 ? "replicado" : "ignorado (versão antiga)");
    }
}
