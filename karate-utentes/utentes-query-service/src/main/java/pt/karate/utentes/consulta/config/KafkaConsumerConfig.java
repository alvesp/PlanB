package pt.karate.utentes.consulta.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;
import pt.karate.aprendizagem.events.TopicosAprendizagem;
import pt.karate.utentes.events.Topicos;

/**
 * Tratamento de erros da projeção.
 *
 * <ul>
 *   <li>Mensagens que não se conseguem ler (JSON inválido) vão logo para {@code <tópico>.DLT}.</li>
 *   <li>Falhas de infraestrutura (OpenSearch indisponível) são repetidas sem limite, com
 *       backoff exponencial até 1 minuto: a projeção pára e retoma sozinha, sem perder eventos.</li>
 * </ul>
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (rec, ex) -> new TopicPartition(rec.topic() + ".DLT", rec.partition()));

        var backOff = new ExponentialBackOff(1_000, 2.0);
        backOff.setMaxInterval(60_000);
        backOff.setMaxElapsedTime(Long.MAX_VALUE);

        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(
                JsonProcessingException.class,
                DeserializationException.class,
                IllegalArgumentException.class);
        return handler;
    }

    @Bean
    NewTopic alunosDlt() {
        return TopicBuilder.name(Topicos.ALUNOS + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic pessoasDlt() {
        return TopicBuilder.name(Topicos.PESSOAS + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic progressoDlt() {
        return TopicBuilder.name(TopicosAprendizagem.PROGRESSO + ".DLT").partitions(3).replicas(1).build();
    }
}
