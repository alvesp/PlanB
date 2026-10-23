package pt.karate.utentes.domain;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "pessoa")
public class Pessoa {

    @Id
    private UUID id;

    private String nome;
    private LocalDate dataNascimento;
    private String email;
    private String telefone;
    private String morada;

    @Embedded
    private DadosFiscais dadosFiscais;

    /** Versão monotónica usada nos eventos (versionamento externo no read-side). */
    private long versaoEvento;

    /** Bloqueio otimista. Wrapper (null = nova) para o Spring Data usar persist e não merge. */
    @Version
    private Long version;

    private Instant criadoEm;
    private Instant atualizadoEm;

    protected Pessoa() {
    }

    public Pessoa(UUID id, String nome, LocalDate dataNascimento, String email, String telefone,
                  String morada, DadosFiscais dadosFiscais, Clock clock) {
        this.id = Objects.requireNonNull(id);
        this.criadoEm = Instant.now(clock);
        atualizar(nome, dataNascimento, email, telefone, morada, clock);
        this.dadosFiscais = dadosFiscais;
    }

    public void atualizar(String nome, LocalDate dataNascimento, String email, String telefone,
                          String morada, Clock clock) {
        if (nome == null || nome.isBlank()) {
            throw new RegraNegocioException("NOME_OBRIGATORIO", "O nome é obrigatório.");
        }
        if (dataNascimento == null || dataNascimento.isAfter(LocalDate.now(clock))) {
            throw new RegraNegocioException("DATA_NASCIMENTO_INVALIDA", "A data de nascimento é inválida.");
        }
        this.nome = nome.strip();
        this.dataNascimento = dataNascimento;
        this.email = email;
        this.telefone = telefone;
        this.morada = morada;
        this.atualizadoEm = Instant.now(clock);
    }

    /** {@code null} remove os dados fiscais. */
    public void definirDadosFiscais(DadosFiscais dadosFiscais, Clock clock) {
        this.dadosFiscais = dadosFiscais;
        this.atualizadoEm = Instant.now(clock);
    }

    public long proximaVersaoEvento() {
        return ++versaoEvento;
    }

    public PoliticaResponsaveis.PessoaInfo info() {
        return new PoliticaResponsaveis.PessoaInfo(id, dataNascimento, dadosFiscais != null);
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

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getMorada() {
        return morada;
    }

    public Optional<DadosFiscais> getDadosFiscais() {
        return Optional.ofNullable(dadosFiscais);
    }

    public long getVersaoEvento() {
        return versaoEvento;
    }

    public Long getVersion() {
        return version;
    }
}
