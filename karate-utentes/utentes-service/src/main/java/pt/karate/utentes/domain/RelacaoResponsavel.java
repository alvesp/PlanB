package pt.karate.utentes.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Relação entre um aluno e um responsável num intervalo de tempo.
 * As linhas não são alteradas: uma mudança de papéis fecha a linha e cria outra,
 * para manter o histórico de quem foi responsável (incluindo fiscal) e quando.
 */
@Entity
@Table(name = "relacao_responsavel")
public class RelacaoResponsavel {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aluno_id")
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsavel_id")
    private Pessoa responsavel;

    @Enumerated(EnumType.STRING)
    private Parentesco parentesco;

    private boolean responsavelLegal;
    private boolean responsavelFiscal;
    private boolean contactoEmergencia;
    private boolean autorizadoRecolha;

    private LocalDate validoDesde;
    private LocalDate validoAte;

    protected RelacaoResponsavel() {
    }

    RelacaoResponsavel(Aluno aluno, Pessoa responsavel, Papeis papeis, LocalDate desde) {
        this.id = UUID.randomUUID();
        this.aluno = aluno;
        this.responsavel = responsavel;
        this.parentesco = papeis.parentesco();
        this.responsavelLegal = papeis.responsavelLegal();
        this.responsavelFiscal = papeis.responsavelFiscal();
        this.contactoEmergencia = papeis.contactoEmergencia();
        this.autorizadoRecolha = papeis.autorizadoRecolha();
        this.validoDesde = desde;
    }

    void terminar(LocalDate em) {
        if (!vigente()) {
            throw new IllegalStateException("Relação já terminada: " + id);
        }
        this.validoAte = em;
    }

    public boolean vigente() {
        return validoAte == null;
    }

    public Papeis papeis() {
        return new Papeis(parentesco, responsavelLegal, responsavelFiscal, contactoEmergencia, autorizadoRecolha);
    }

    PoliticaResponsaveis.RelacaoInfo info() {
        return new PoliticaResponsaveis.RelacaoInfo(responsavel.info(), parentesco, responsavelLegal, responsavelFiscal);
    }

    public UUID getId() {
        return id;
    }

    public Pessoa getResponsavel() {
        return responsavel;
    }

    public Parentesco getParentesco() {
        return parentesco;
    }

    public boolean isResponsavelLegal() {
        return responsavelLegal;
    }

    public boolean isResponsavelFiscal() {
        return responsavelFiscal;
    }

    public boolean isContactoEmergencia() {
        return contactoEmergencia;
    }

    public boolean isAutorizadoRecolha() {
        return autorizadoRecolha;
    }

    public LocalDate getValidoDesde() {
        return validoDesde;
    }

    public LocalDate getValidoAte() {
        return validoAte;
    }
}
