package pt.karate.utentes.integracao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import pt.karate.aprendizagem.events.GraduacaoAtribuida;
import pt.karate.aprendizagem.events.TopicosAprendizagem;
import pt.karate.utentes.app.AlunoService;

/**
 * Atualiza a graduação atual na ficha do aluno quando o aprendizagem-service atribui uma
 * graduação. O offset só avança depois de a transação confirmar (AckMode RECORD).
 */
@Component
public class GraduacoesConsumer {

    private final AlunoService alunos;
    private final ObjectMapper objectMapper;

    public GraduacoesConsumer(AlunoService alunos, ObjectMapper objectMapper) {
        this.alunos = alunos;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = TopicosAprendizagem.GRADUACOES, groupId = "${utentes.kafka.grupo:utentes-service}")
    public void graduacao(ConsumerRecord<String, String> rec) throws JsonProcessingException {
        alunos.aplicarGraduacao(objectMapper.readValue(rec.value(), GraduacaoAtribuida.class));
    }
}
