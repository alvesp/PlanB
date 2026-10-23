package pt.karate.utentes.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
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
}
