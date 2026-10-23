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

/**
 * Exame de graduação (ou reconhecimento de graduação obtida noutro clube, com dispensa
 * de requisitos). A validação das regras é feita em {@link RequisitosGraduacao#validarExame}
 * antes da construção.
 */
@Entity
@Table(name = "exame_graduacao")
public class ExameGraduacao {

    @Id
    private UUID id;

    private UUID alunoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "graduacao_codigo")
    private Graduacao graduacao;

    private LocalDate data;
    private String examinador;

    @Enumerated(EnumType.STRING)
    private ResultadoExame resultado;

    private String observacoes;
    private boolean requisitosDispensados;
    private String motivoDispensa;
    private Instant registadoEm;

    @Version
    private Long version;

    protected ExameGraduacao() {
    }

    public ExameGraduacao(AlunoRef aluno, Graduacao graduacao, LocalDate data, String examinador,
                          ResultadoExame resultado, String observacoes, boolean requisitosDispensados,
                          String motivoDispensa, Clock clock) {
        aluno.garantirAtivo();
        if (examinador == null || examinador.isBlank()) {
            throw new RegraNegocioException("EXAMINADOR_OBRIGATORIO", "Indique o examinador.");
        }
        this.id = UUID.randomUUID();
        this.alunoId = aluno.getId();
        this.graduacao = Objects.requireNonNull(graduacao);
        this.data = Objects.requireNonNull(data);
        this.examinador = examinador.strip();
        this.resultado = Objects.requireNonNull(resultado);
        this.observacoes = observacoes;
        this.requisitosDispensados = requisitosDispensados;
        this.motivoDispensa = requisitosDispensados ? motivoDispensa : null;
        this.registadoEm = Instant.now(clock);
    }

    public boolean aprovado() {
        return resultado == ResultadoExame.APROVADO;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAlunoId() {
        return alunoId;
    }

    public Graduacao getGraduacao() {
        return graduacao;
    }

    public LocalDate getData() {
        return data;
    }

    public String getExaminador() {
        return examinador;
    }

    public ResultadoExame getResultado() {
        return resultado;
    }

    public String getObservacoes() {
        return observacoes;
    }

    public boolean isRequisitosDispensados() {
        return requisitosDispensados;
    }

    public String getMotivoDispensa() {
        return motivoDispensa;
    }
}
