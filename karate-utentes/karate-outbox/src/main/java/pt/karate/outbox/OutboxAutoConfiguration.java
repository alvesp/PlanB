package pt.karate.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Ativa a outbox em qualquer serviço que tenha este módulo no classpath.
 *
 * <p>{@code @AutoConfigurationPackage} junta {@code pt.karate.outbox} aos pacotes onde o
 * Spring procura entidades JPA e repositórios, por isso corre antes da auto-configuração do JPA.
 * O serviço só precisa de criar a tabela {@code outbox_evento} na sua migração Flyway.
 */
@AutoConfiguration(before = {HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
@AutoConfigurationPackage(basePackageClasses = OutboxEvento.class)
@EnableConfigurationProperties(OutboxProperties.class)
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    OutboxWriter outboxWriter(ObjectMapper objectMapper, Clock clock) {
        return new OutboxWriter(objectMapper, clock);
    }

    @Bean
    OutboxRelay outboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafka,
                            TransactionTemplate tx, Clock clock, OutboxProperties props) {
        return new OutboxRelay(repository, kafka, tx, clock, props);
    }
}
