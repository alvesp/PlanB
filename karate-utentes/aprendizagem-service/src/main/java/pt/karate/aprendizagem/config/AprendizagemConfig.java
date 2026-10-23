package pt.karate.aprendizagem.config;

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

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

@Configuration
public class AprendizagemConfig {

    @Bean
    Clock clock(@Value("${aprendizagem.fuso-horario:Europe/Lisbon}") String fuso) {
        return Clock.system(ZoneId.of(fuso));
    }

    /** Snapshots de progresso: só interessa o último por aluno. */
    @Bean
    NewTopic topicoProgresso(@Value("${aprendizagem.kafka.particoes:3}") int particoes,
                             @Value("${aprendizagem.kafka.replicas:1}") short replicas) {
        return TopicBuilder.name(TopicosAprendizagem.PROGRESSO).partitions(particoes).replicas(replicas)
                .compact().build();
    }

    /** Factos (uma mensagem por graduação obtida): retenção longa, sem compactação. */
    @Bean
    NewTopic topicoGraduacoes(@Value("${aprendizagem.kafka.particoes:3}") int particoes,
                              @Value("${aprendizagem.kafka.replicas:1}") short replicas) {
        return TopicBuilder.name(TopicosAprendizagem.GRADUACOES).partitions(particoes).replicas(replicas)
                .config("retention.ms", Long.toString(Duration.ofDays(365).toMillis())).build();
    }

    /**
     * Réplica de alunos: JSON inválido vai para a DLT; erros de base de dados repetem
     * com backoff até 1 minuto, sem limite.
     */
    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (rec, ex) -> new TopicPartition(rec.topic() + ".aprendizagem.DLT", rec.partition()));
        var backOff = new ExponentialBackOff(1_000, 2.0);
        backOff.setMaxInterval(60_000);
        backOff.setMaxElapsedTime(Long.MAX_VALUE);
        var handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        return handler;
    }

    @Bean
    NewTopic alunosDlt() {
        return TopicBuilder.name(pt.karate.utentes.events.Topicos.ALUNOS + ".aprendizagem.DLT")
                .partitions(3).replicas(1).build();
    }
}
