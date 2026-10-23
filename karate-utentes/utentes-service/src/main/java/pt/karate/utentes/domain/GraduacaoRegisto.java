package pt.karate.utentes.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "graduacao_registo")
public class GraduacaoRegisto {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aluno_id")
    private Aluno aluno;

    /** Exame no aprendizagem-service que originou este registo (chave de idempotência). */
    private UUID exameId;
    private String graduacaoCodigo;
    private String graduacao;
    private int ordem;
    private LocalDate dataExame;
    private String examinador;

    protected GraduacaoRegisto() {
    }

    GraduacaoRegisto(Aluno aluno, UUID exameId, String graduacaoCodigo, String graduacao, int ordem,
                     LocalDate dataExame, String examinador) {
        this.id = UUID.randomUUID();
        this.aluno = aluno;
        this.exameId = exameId;
        this.graduacaoCodigo = graduacaoCodigo;
        this.graduacao = graduacao;
        this.ordem = ordem;
        this.dataExame = dataExame;
        this.examinador = examinador;
    }

    public UUID getExameId() {
        return exameId;
    }

    public String getGraduacaoCodigo() {
        return graduacaoCodigo;
    }

    public int getOrdem() {
        return ordem;
    }

    public UUID getId() {
        return id;
    }

    public String getGraduacao() {
        return graduacao;
    }

    public LocalDate getDataExame() {
        return dataExame;
    }

    public String getExaminador() {
        return examinador;
    }
}
