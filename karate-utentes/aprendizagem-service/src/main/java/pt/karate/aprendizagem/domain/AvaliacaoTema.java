package pt.karate.aprendizagem.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Registo do nível de domínio de um tema por um aluno numa data. */
@Entity
@Table(name = "avaliacao_tema")
public class AvaliacaoTema {

    @Id
    private UUID id;

    private UUID alunoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tema_id")
    private Tema tema;

    @Enumerated(EnumType.STRING)
    private NivelDominio nivel;

    private LocalDate data;
    private String avaliador;
    private String observacao;
    private Instant registadoEm;

    @Version
    private Long version;

    protected AvaliacaoTema() {
    }

    public AvaliacaoTema(AlunoRef aluno, Tema tema, NivelDominio nivel, LocalDate data, String avaliador,
                         String observacao, Clock clock) {
        aluno.garantirAtivo();
        if (!tema.isAtivo()) {
            throw new RegraNegocioException("TEMA_INATIVO", "O tema " + tema.getNome() + " está inativo.");
        }
        if (data == null || data.isAfter(LocalDate.now(clock))) {
            throw new RegraNegocioException("DATA_AVALIACAO_INVALIDA", "A data da avaliação não pode ser futura.");
        }
        this.id = UUID.randomUUID();
        this.alunoId = aluno.getId();
        this.tema = tema;
        this.nivel = Objects.requireNonNull(nivel);
        this.data = data;
        this.avaliador = avaliador;
        this.observacao = observacao;
        this.registadoEm = Instant.now(clock);
    }

    public UUID getId() {
        return id;
    }

    public UUID getAlunoId() {
        return alunoId;
    }

    public Tema getTema() {
        return tema;
    }

    public NivelDominio getNivel() {
        return nivel;
    }

    public LocalDate getData() {
        return data;
    }

    public String getAvaliador() {
        return avaliador;
    }

    public String getObservacao() {
        return observacao;
    }
}
