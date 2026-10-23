package pt.karate.aprendizagem.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Cópia local, só de leitura, de um aluno do utentes-service. É escrita pelo consumidor
 * Kafka ({@code ReplicaAlunos}) com SQL nativo e idempotente, nunca através do JPA.
 */
@Entity
@Table(name = "aluno_ref")
public class AlunoRef {

    @Id
    private UUID id;

    private String nome;
    private LocalDate dataNascimento;
    private LocalDate dataInscricao;
    private String estado;
    private long versao;

    protected AlunoRef() {
    }

    /** Usado em testes. */
    public AlunoRef(UUID id, String nome, LocalDate dataNascimento, LocalDate dataInscricao, String estado) {
        this.id = id;
        this.nome = nome;
        this.dataNascimento = dataNascimento;
        this.dataInscricao = dataInscricao;
        this.estado = estado;
    }

    /** Alunos inativos não podem ter presenças, avaliações ou exames novos. */
    public void garantirAtivo() {
        if ("INATIVO".equals(estado)) {
            throw new RegraNegocioException("ALUNO_INATIVO", "O aluno " + nome + " está inativo.");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public LocalDate getDataInscricao() {
        return dataInscricao;
    }

    public String getEstado() {
        return estado;
    }

    public long getVersao() {
        return versao;
    }
}
