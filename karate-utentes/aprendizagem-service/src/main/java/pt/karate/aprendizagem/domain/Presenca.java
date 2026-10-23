package pt.karate.aprendizagem.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "presenca")
public class Presenca {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aula_id")
    private Aula aula;

    private UUID alunoId;

    @Enumerated(EnumType.STRING)
    private EstadoPresenca estado;

    private String observacao;
    private Instant registadoEm;

    protected Presenca() {
    }

    Presenca(Aula aula, UUID alunoId, EstadoPresenca estado, String observacao, Clock clock) {
        this.id = UUID.randomUUID();
        this.aula = aula;
        this.alunoId = alunoId;
        this.estado = estado;
        this.observacao = observacao;
        this.registadoEm = Instant.now(clock);
    }

    boolean alterar(EstadoPresenca novo, String observacao, Clock clock) {
        if (estado == novo && Objects.equals(this.observacao, observacao)) {
            return false;
        }
        this.estado = novo;
        this.observacao = observacao;
        this.registadoEm = Instant.now(clock);
        return true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAlunoId() {
        return alunoId;
    }

    public EstadoPresenca getEstado() {
        return estado;
    }

    public String getObservacao() {
        return observacao;
    }

    public Instant getRegistadoEm() {
        return registadoEm;
    }
}
