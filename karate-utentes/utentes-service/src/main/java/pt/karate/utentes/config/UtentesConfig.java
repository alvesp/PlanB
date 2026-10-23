package pt.karate.utentes.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;
import pt.karate.aprendizagem.events.TopicosAprendizagem;
import pt.karate.utentes.domain.NaoEncontradoException;
import pt.karate.utentes.events.Topicos;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class UtentesConfig {

    /** Relógio único da aplicação: datas de negócio (idade, validade) em hora de Lisboa. */
    @Bean
    Clock clock(@Value("${utentes.fuso-horario:Europe/Lisbon}") String fuso) {
        return Clock.system(ZoneId.of(fuso));
    }

    /**
     * Tópicos compactados: só interessa o último snapshot de cada agregado, o que permite
     * reconstruir o read-side do zero lendo o tópico desde o início.
     */
    @Bean
    NewTopic topicoAlunos(@Value("${utentes.kafka.particoes:3}") int particoes,
                          @Value("${utentes.kafka.replicas:1}") short replicas) {
        return TopicBuilder.name(Topicos.ALUNOS).partitions(particoes).replicas(replicas).compact().build();
    }

    @Bean
    NewTopic topicoPessoas(@Value("${utentes.kafka.particoes:3}") int particoes,
                           @Value("${utentes.kafka.replicas:1}") short replicas) {
        return TopicBuilder.name(Topicos.PESSOAS).partitions(particoes).replicas(replicas).compact().build();
    }

    /**
     * Consumo de graduações: JSON inválido ou aluno inexistente vão para a DLT; outros erros
     * (ex.: base de dados indisponível) repetem com backoff até 1 minuto, sem limite.
     */
    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (rec, ex) -> new TopicPartition(rec.topic() + ".utentes.DLT", rec.partition()));
        var backOff = new ExponentialBackOff(1_000, 2.0);
        backOff.setMaxInterval(60_000);
        backOff.setMaxElapsedTime(Long.MAX_VALUE);
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JsonProcessingException.class, NaoEncontradoException.class,
                IllegalArgumentException.class);
        return handler;
    }

    @Bean
    NewTopic graduacoesDlt() {
        return TopicBuilder.name(TopicosAprendizagem.GRADUACOES + ".utentes.DLT").partitions(3).replicas(1).build();
    }
}
