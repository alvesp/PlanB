package pt.karate.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Grava eventos na outbox. Exige uma transação já aberta: o evento só existe se a
 * alteração de negócio que o originou for confirmada.
 */
public class OutboxWriter {

    @PersistenceContext
    private EntityManager em;

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registar(String aggregateType, UUID aggregateId, long aggregateVersion,
                         String eventType, String topic, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar o evento " + eventType, e);
        }
        // persist (e não save/merge): o id é atribuído pela aplicação.
        em.persist(new OutboxEvento(aggregateType, aggregateId, aggregateVersion,
                eventType, topic, json, Instant.now(clock)));
    }
}
