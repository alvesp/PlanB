package pt.karate.utentes.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Agregado aluno. Todas as alterações às relações com responsáveis passam por aqui e
 * terminam com {@link #validar(LocalDate)}, que aplica {@link PoliticaResponsaveis}.
 */
@Entity
@Table(name = "aluno")
public class Aluno {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pessoa_id")
    private Pessoa pessoa;

    @Enumerated(EnumType.STRING)
    private EstadoAluno estado;

    private String numFederacao;
    private String graduacaoAtual;
    private LocalDate dataInscricao;

    @OneToMany(mappedBy = "aluno", cascade = CascadeType.ALL)
    @OrderBy("validoDesde")
    private List<RelacaoResponsavel> relacoes = new ArrayList<>();

    @OneToMany(mappedBy = "aluno", cascade = CascadeType.ALL)
    @OrderBy("dataExame")
    private List<GraduacaoRegisto> graduacoes = new ArrayList<>();

    private long versaoEvento;

    /** Bloqueio otimista. Wrapper (null = nova) para o Spring Data usar persist e não merge. */
    @Version
    private Long version;

    private Instant criadoEm;
    private Instant atualizadoEm;

    protected Aluno() {
    }

    /**
     * Inscreve um aluno já com os seus responsáveis, para que o agregado nunca exista
     * num estado inválido (ex.: menor sem responsável fiscal).
     */
    public static Aluno inscrever(UUID id, Pessoa pessoa, String numFederacao, String graduacaoAtual,
                                  Map<Pessoa, Papeis> responsaveis, Clock clock) {
        LocalDate hoje = LocalDate.now(clock);
        Aluno aluno = new Aluno();
        aluno.id = Objects.requireNonNull(id);
        aluno.pessoa = Objects.requireNonNull(pessoa);
        aluno.estado = EstadoAluno.ATIVO;
        aluno.numFederacao = numFederacao;
        aluno.graduacaoAtual = graduacaoAtual;
        aluno.dataInscricao = hoje;
        aluno.criadoEm = Instant.now(clock);
        aluno.atualizadoEm = aluno.criadoEm;
        responsaveis.forEach((responsavel, papeis) ->
                aluno.relacoes.add(new RelacaoResponsavel(aluno, responsavel, papeis, hoje)));
        aluno.validar(hoje);
        return aluno;
    }

    public void adicionarResponsavel(Pessoa responsavel, Papeis papeis, Clock clock) {
        LocalDate hoje = LocalDate.now(clock);
        if (relacaoVigente(responsavel.getId()).isPresent()) {
            throw new RegraNegocioException("RELACAO_JA_EXISTE",
                    "Esta pessoa já é responsável pelo aluno. Use a alteração de papéis.");
        }
        if (papeis.responsavelFiscal()) {
            retirarPapelFiscalAtual(hoje);
        }
        relacoes.add(new RelacaoResponsavel(this, responsavel, papeis, hoje));
        tocar(clock);
        validar(hoje);
    }

    public void alterarPapeis(UUID responsavelId, Papeis novos, Clock clock) {
        LocalDate hoje = LocalDate.now(clock);
        RelacaoResponsavel atual = relacaoVigente(responsavelId)
                .orElseThrow(() -> new RegraNegocioException("RELACAO_INEXISTENTE",
                        "Esta pessoa não é responsável vigente pelo aluno."));
        if (atual.papeis().equals(novos)) {
            return;
        }
        atual.terminar(hoje);
        if (novos.responsavelFiscal()) {
            retirarPapelFiscalAtual(hoje);
        }
        relacoes.add(new RelacaoResponsavel(this, atual.getResponsavel(), novos, hoje));
        tocar(clock);
        validar(hoje);
    }

    /** Passa a responsabilidade fiscal para um responsável que já está associado ao aluno. */
    public void definirResponsavelFiscal(UUID responsavelId, Clock clock) {
        RelacaoResponsavel relacao = relacaoVigente(responsavelId)
                .orElseThrow(() -> new RegraNegocioException("RELACAO_INEXISTENTE",
                        "O novo responsável fiscal tem de estar associado ao aluno."));
        alterarPapeis(responsavelId, relacao.papeis().comFiscal(true), clock);
    }

    /**
     * Termina a relação com um responsável. Falha se deixar o aluno sem responsável fiscal
     * ou, sendo menor, sem responsável legal; nesse caso transfira primeiro o papel.
     */
    public void terminarRelacao(UUID responsavelId, Clock clock) {
        LocalDate hoje = LocalDate.now(clock);
        RelacaoResponsavel relacao = relacaoVigente(responsavelId)
                .orElseThrow(() -> new RegraNegocioException("RELACAO_INEXISTENTE",
                        "Esta pessoa não é responsável vigente pelo aluno."));
        relacao.terminar(hoje);
        tocar(clock);
        validar(hoje);
    }

    public void registarGraduacao(String graduacao, LocalDate dataExame, String examinador, Clock clock) {
        if (graduacao == null || graduacao.isBlank()) {
            throw new RegraNegocioException("GRADUACAO_OBRIGATORIA", "A graduação é obrigatória.");
        }
        if (dataExame.isAfter(LocalDate.now(clock))) {
            throw new RegraNegocioException("DATA_EXAME_FUTURA", "A data do exame não pode ser futura.");
        }
        graduacoes.add(new GraduacaoRegisto(this, graduacao.strip(), dataExame, examinador));
        boolean maisRecente = graduacoes.stream().allMatch(g -> !g.getDataExame().isAfter(dataExame));
        if (maisRecente) {
            graduacaoAtual = graduacao.strip();
        }
        tocar(clock);
    }

    public void atualizarDados(String numFederacao, EstadoAluno estado, Clock clock) {
        this.numFederacao = numFederacao;
        this.estado = Objects.requireNonNull(estado);
        tocar(clock);
    }

    /** Revalida as regras (ex.: após alterar a data de nascimento ou os dados fiscais de uma pessoa). */
    public void validar(LocalDate hoje) {
        PoliticaResponsaveis.validar(
                pessoa.info(),
                relacoesVigentes().stream().map(RelacaoResponsavel::info).toList(),
                hoje);
    }

    public long proximaVersaoEvento() {
        return ++versaoEvento;
    }

    public List<RelacaoResponsavel> relacoesVigentes() {
        return relacoes.stream().filter(RelacaoResponsavel::vigente).toList();
    }

    public Optional<RelacaoResponsavel> responsavelFiscal() {
        return relacoesVigentes().stream().filter(RelacaoResponsavel::isResponsavelFiscal).findFirst();
    }

    private Optional<RelacaoResponsavel> relacaoVigente(UUID responsavelId) {
        return relacoesVigentes().stream()
                .filter(r -> r.getResponsavel().getId().equals(responsavelId))
                .findFirst();
    }

    private void retirarPapelFiscalAtual(LocalDate hoje) {
        responsavelFiscal().ifPresent(atual -> {
            atual.terminar(hoje);
            relacoes.add(new RelacaoResponsavel(this, atual.getResponsavel(), atual.papeis().comFiscal(false), hoje));
        });
    }

    private void tocar(Clock clock) {
        this.atualizadoEm = Instant.now(clock);
    }

    public UUID getId() {
        return id;
    }

    public Pessoa getPessoa() {
        return pessoa;
    }

    public EstadoAluno getEstado() {
        return estado;
    }

    public String getNumFederacao() {
        return numFederacao;
    }

    public String getGraduacaoAtual() {
        return graduacaoAtual;
    }

    public LocalDate getDataInscricao() {
        return dataInscricao;
    }

    public List<RelacaoResponsavel> getRelacoes() {
        return List.copyOf(relacoes);
    }

    public List<GraduacaoRegisto> getGraduacoes() {
        return List.copyOf(graduacoes);
    }

    public long getVersaoEvento() {
        return versaoEvento;
    }

    public Long getVersion() {
        return version;
    }
}
