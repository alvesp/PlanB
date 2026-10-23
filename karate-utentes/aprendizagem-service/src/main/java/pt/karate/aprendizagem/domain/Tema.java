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

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Tema de aprendizagem (ex.: "Heian Shodan", "Gyaku-zuki", "Gohon kumite").
 * Se tiver graduação, faz parte do programa de exame dessa graduação.
 */
@Entity
@Table(name = "tema")
public class Tema {

    @Id
    private UUID id;

    private String nome;

    @Enumerated(EnumType.STRING)
    private CategoriaTema categoria;

    private String descricao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graduacao_codigo")
    private Graduacao graduacao;

    private boolean ativo;

    @Version
    private Long version;

    protected Tema() {
    }

    public Tema(UUID id, String nome, CategoriaTema categoria, String descricao, Graduacao graduacao) {
        this.id = Objects.requireNonNull(id);
        this.ativo = true;
        atualizar(nome, categoria, descricao, graduacao, true);
    }

    public void atualizar(String nome, CategoriaTema categoria, String descricao, Graduacao graduacao, boolean ativo) {
        if (nome == null || nome.isBlank()) {
            throw new RegraNegocioException("NOME_OBRIGATORIO", "O nome do tema é obrigatório.");
        }
        this.nome = nome.strip();
        this.categoria = Objects.requireNonNull(categoria);
        this.descricao = descricao;
        this.graduacao = graduacao;
        this.ativo = ativo;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public CategoriaTema getCategoria() {
        return categoria;
    }

    public String getDescricao() {
        return descricao;
    }

    public Optional<Graduacao> getGraduacao() {
        return Optional.ofNullable(graduacao);
    }

    public boolean isAtivo() {
        return ativo;
    }
}
