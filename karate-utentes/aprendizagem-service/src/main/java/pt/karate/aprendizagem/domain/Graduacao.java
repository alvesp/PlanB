package pt.karate.aprendizagem.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Graduação do catálogo (ex.: 8º Kyu, faixa laranja). */
@Entity
@Table(name = "graduacao")
public class Graduacao {

    @Id
    private String codigo;

    private String nome;
    private String corFaixa;

    @Enumerated(EnumType.STRING)
    private TipoGraduacao tipo;

    private int ordem;
    private int presencasMinimas;
    private int mesesMinimos;
    private boolean ativa;

    @Version
    private Long version;

    protected Graduacao() {
    }

    public Graduacao(String codigo, String nome, String corFaixa, TipoGraduacao tipo, int ordem,
                     int presencasMinimas, int mesesMinimos) {
        if (codigo == null || !codigo.matches("[A-Z0-9_]{1,20}")) {
            throw new RegraNegocioException("CODIGO_GRADUACAO_INVALIDO",
                    "O código tem de ter 1 a 20 letras maiúsculas, dígitos ou '_' (ex.: 8KYU).");
        }
        this.codigo = codigo;
        this.ativa = true;
        atualizar(nome, corFaixa, tipo, ordem, presencasMinimas, mesesMinimos, true);
    }

    public void atualizar(String nome, String corFaixa, TipoGraduacao tipo, int ordem,
                          int presencasMinimas, int mesesMinimos, boolean ativa) {
        if (nome == null || nome.isBlank() || corFaixa == null || corFaixa.isBlank()) {
            throw new RegraNegocioException("GRADUACAO_INCOMPLETA", "Nome e cor da faixa são obrigatórios.");
        }
        if (ordem < 1 || presencasMinimas < 0 || mesesMinimos < 0) {
            throw new RegraNegocioException("GRADUACAO_VALORES_INVALIDOS",
                    "A ordem tem de ser positiva e os mínimos não podem ser negativos.");
        }
        this.nome = nome.strip();
        this.corFaixa = corFaixa.strip();
        this.tipo = java.util.Objects.requireNonNull(tipo);
        this.ordem = ordem;
        this.presencasMinimas = presencasMinimas;
        this.mesesMinimos = mesesMinimos;
        this.ativa = ativa;
    }

    public RequisitosGraduacao.GraduacaoRef ref() {
        return new RequisitosGraduacao.GraduacaoRef(codigo, nome, ordem, presencasMinimas, mesesMinimos);
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNome() {
        return nome;
    }

    public String getCorFaixa() {
        return corFaixa;
    }

    public TipoGraduacao getTipo() {
        return tipo;
    }

    public int getOrdem() {
        return ordem;
    }

    public int getPresencasMinimas() {
        return presencasMinimas;
    }

    public int getMesesMinimos() {
        return mesesMinimos;
    }

    public boolean isAtiva() {
        return ativa;
    }
}
