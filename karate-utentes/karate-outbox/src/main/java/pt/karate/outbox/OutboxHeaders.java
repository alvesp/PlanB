package pt.karate.outbox;

/**
 * Cabeçalhos Kafka acrescentados pelo {@link OutboxRelay} a cada mensagem.
 */
public final class OutboxHeaders {

    public static final String EVENT_ID = "event-id";
    public static final String EVENT_TYPE = "event-type";
    public static final String AGGREGATE_VERSION = "aggregate-version";

    private OutboxHeaders() {
    }
}
