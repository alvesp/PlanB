package pt.karate.aprendizagem.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Grupo de treino (ex.: "Infantis 6-10", "Adultos avançados"). */
@Entity
@Table(name = "turma")
public class Turma {

    @Id
    private UUID id;

    private String nome;
    private String descricao;
    private String instrutor;
    private boolean ativa;

    @ElementCollection
    @CollectionTable(name = "turma_aluno", joinColumns = @JoinColumn(name = "turma_id"))
    @Column(name = "aluno_id")
    private Set<UUID> alunos = new HashSet<>();

    @Version
    private Long version;

    protected Turma() {
    }

    public Turma(UUID id, String nome, String descricao, String instrutor) {
        this.id = Objects.requireNonNull(id);
        atualizar(nome, descricao, instrutor, true);
    }

    public void atualizar(String nome, String descricao, String instrutor, boolean ativa) {
        if (nome == null || nome.isBlank()) {
            throw new RegraNegocioException("NOME_OBRIGATORIO", "O nome da turma é obrigatório.");
        }
        this.nome = nome.strip();
        this.descricao = descricao;
        this.instrutor = instrutor;
        this.ativa = ativa;
    }

    /** @return {@code false} se o aluno já pertencia à turma. */
    public boolean adicionarAluno(AlunoRef aluno) {
        if (!ativa) {
            throw new RegraNegocioException("TURMA_INATIVA", "Não é possível juntar alunos a uma turma inativa.");
        }
        aluno.garantirAtivo();
        return alunos.add(aluno.getId());
    }

    public boolean removerAluno(UUID alunoId) {
        return alunos.remove(alunoId);
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getInstrutor() {
        return instrutor;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public Set<UUID> getAlunos() {
        return Set.copyOf(alunos);
    }
}
