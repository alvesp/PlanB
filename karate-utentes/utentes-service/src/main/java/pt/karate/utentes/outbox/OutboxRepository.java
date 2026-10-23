package pt.karate.utentes.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvento, UUID> {

    /**
     * Reserva um lote de eventos pendentes. {@code SKIP LOCKED} permite correr várias
     * instâncias do serviço sem publicarem o mesmo evento em simultâneo.
     */
    @Query(value = """
            select * from outbox_evento
            where publicado_em is null
            order by criado_em
            limit :limite
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvento> reservarPendentes(int limite);

    @Modifying
    @Query("delete from OutboxEvento e where e.publicadoEm is not null and e.publicadoEm < :antesDe")
    int apagarPublicadosAntesDe(Instant antesDe);

    @Query("select count(e) from OutboxEvento e where e.publicadoEm is null")
    long contarPendentes();
}
