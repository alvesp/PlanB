package pt.karate.utentes.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_evento")
public class OutboxEvento {

    @Id
    private UUID id;

    private String aggregateType;
    private UUID aggregateId;
    private long aggregateVersion;
    private String eventType;
    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String payload;

    private Instant criadoEm;
    private Instant publicadoEm;
    private int tentativas;
    private String ultimoErro;

    protected OutboxEvento() {
    }

    OutboxEvento(String aggregateType, UUID aggregateId, long aggregateVersion,
                 String eventType, String topic, String payload, Instant criadoEm) {
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.aggregateVersion = aggregateVersion;
        this.eventType = eventType;
        this.topic = topic;
        this.payload = payload;
        this.criadoEm = criadoEm;
    }

    void marcarPublicado(Instant em) {
        this.publicadoEm = em;
        this.ultimoErro = null;
    }

    void registarFalha(String erro) {
        this.tentativas++;
        this.ultimoErro = erro == null ? null : erro.substring(0, Math.min(erro.length(), 2000));
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public long getAggregateVersion() {
        return aggregateVersion;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public String getPayload() {
        return payload;
    }

    public int getTentativas() {
        return tentativas;
    }
}
