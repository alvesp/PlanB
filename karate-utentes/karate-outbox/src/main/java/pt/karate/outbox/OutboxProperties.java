package pt.karate.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuração da outbox ({@code outbox.*}). O intervalo e o cron são lidos diretamente
 * pelas anotações {@code @Scheduled}: {@code outbox.intervalo-ms} e {@code outbox.limpeza-cron}.
 */
@ConfigurationProperties("outbox")
public record OutboxProperties(
        @DefaultValue("100") int tamanhoLote,
        @DefaultValue("PT30S") Duration timeoutEnvio,
        @DefaultValue("P7D") Duration retencao
) {
}
