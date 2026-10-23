package pt.karate.utentes.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.karate.utentes.domain.Aluno;
import pt.karate.utentes.domain.EstadoAluno;
import pt.karate.utentes.domain.NaoEncontradoException;
import pt.karate.utentes.domain.Papeis;
import pt.karate.utentes.domain.Pessoa;
import pt.karate.utentes.domain.RegraNegocioException;
import pt.karate.utentes.domain.RelacaoResponsavel;
import pt.karate.aprendizagem.events.GraduacaoAtribuida;
import pt.karate.utentes.app.Vistas.AlunoView;
import pt.karate.utentes.app.Vistas.ResponsavelFiscalView;
import pt.karate.utentes.repo.AlunoRepository;
import pt.karate.utentes.repo.PessoaRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class AlunoService {

    public record NovoResponsavel(UUID pessoaId, Papeis papeis) {
    }

    public record NovaInscricao(UUID pessoaId, String numFederacao, List<NovoResponsavel> responsaveis) {
    }

    private final AlunoRepository alunos;
    private final PessoaRepository pessoas;
    private final EventosUtentes eventos;
    private final Clock clock;

    public AlunoService(AlunoRepository alunos, PessoaRepository pessoas, EventosUtentes eventos, Clock clock) {
        this.alunos = alunos;
        this.pessoas = pessoas;
        this.eventos = eventos;
        this.clock = clock;
    }

    @Transactional
    public AlunoView inscrever(NovaInscricao cmd) {
        if (alunos.existsByPessoaId(cmd.pessoaId())) {
            throw new RegraNegocioException("ALUNO_JA_INSCRITO", "Esta pessoa já está inscrita como aluno.");
        }
        Pessoa pessoa = pessoa(cmd.pessoaId());

        Map<Pessoa, Papeis> responsaveis = new LinkedHashMap<>();
        for (NovoResponsavel r : cmd.responsaveis()) {
            if (responsaveis.put(pessoa(r.pessoaId()), r.papeis()) != null) {
                throw new RegraNegocioException("RESPONSAVEL_DUPLICADO",
                        "O mesmo responsável foi indicado mais do que uma vez.");
            }
        }

        Aluno aluno = Aluno.inscrever(UUID.randomUUID(), pessoa, cmd.numFederacao(), responsaveis, clock);
        alunos.save(aluno);
        eventos.emitir("Inscrito", List.of(aluno), responsaveis.keySet());
        return Vistas.de(aluno, false);
    }

    @Transactional
    public AlunoView adicionarResponsavel(UUID alunoId, UUID pessoaId, Papeis papeis) {
        Pessoa responsavel = pessoa(pessoaId);
        return alterarRelacoes(alunoId, a -> a.adicionarResponsavel(responsavel, papeis, clock));
    }

    @Transactional
    public AlunoView alterarPapeis(UUID alunoId, UUID pessoaId, Papeis papeis) {
        return alterarRelacoes(alunoId, a -> a.alterarPapeis(pessoaId, papeis, clock));
    }

    @Transactional
    public AlunoView definirResponsavelFiscal(UUID alunoId, UUID pessoaId) {
        return alterarRelacoes(alunoId, a -> a.definirResponsavelFiscal(pessoaId, clock));
    }

    @Transactional
    public AlunoView terminarRelacao(UUID alunoId, UUID pessoaId) {
        return alterarRelacoes(alunoId, a -> a.terminarRelacao(pessoaId, clock));
    }

    /**
     * Aplica uma graduação atribuída no aprendizagem-service (consumidor Kafka).
     * Idempotente: um evento repetido não altera nada nem emite novo snapshot.
     */
    @Transactional
    public void aplicarGraduacao(GraduacaoAtribuida evento) {
        Aluno aluno = carregar(evento.alunoId());
        boolean alterado = aluno.aplicarGraduacao(evento.exameId(), evento.graduacaoCodigo(), evento.graduacaoNome(),
                evento.ordem(), evento.data(), evento.examinador(), clock);
        if (alterado) {
            eventos.emitir("Atualizado", List.of(aluno), List.of());
        }
    }

    @Transactional
    public AlunoView atualizarDados(UUID alunoId, String numFederacao, EstadoAluno estado) {
        Aluno aluno = carregar(alunoId);
        aluno.atualizarDados(numFederacao, estado, clock);
        eventos.emitir("Atualizado", List.of(aluno), List.of());
        return Vistas.de(aluno, false);
    }

    @Transactional(readOnly = true)
    public AlunoView obter(UUID id, boolean historico) {
        return Vistas.de(carregar(id), historico);
    }

    /** Quem deve ser faturado pelas mensalidades deste aluno. */
    @Transactional(readOnly = true)
    public ResponsavelFiscalView responsavelFiscal(UUID alunoId) {
        return carregar(alunoId).responsavelFiscal()
                .map(Vistas::fiscal)
                .orElseThrow(() -> new IllegalStateException("Aluno sem responsável fiscal: " + alunoId));
    }

    private Aluno carregar(UUID id) {
        return alunos.findById(id).orElseThrow(() -> new NaoEncontradoException("Aluno", id));
    }

    /**
     * Aplica uma alteração às relações e emite o aluno e todas as pessoas cuja lista
     * de alunos/papéis pode ter mudado (responsáveis antes e depois da alteração).
     */
    private AlunoView alterarRelacoes(UUID alunoId, Consumer<Aluno> alteracao) {
        Aluno aluno = carregar(alunoId);
        Set<Pessoa> envolvidas = new LinkedHashSet<>(responsaveisVigentes(aluno));
        alteracao.accept(aluno);
        envolvidas.addAll(responsaveisVigentes(aluno));
        eventos.emitir("Atualizado", List.of(aluno), envolvidas);
        return Vistas.de(aluno, false);
    }

    private static List<Pessoa> responsaveisVigentes(Aluno aluno) {
        return aluno.relacoesVigentes().stream().map(RelacaoResponsavel::getResponsavel).toList();
    }

    private Pessoa pessoa(UUID id) {
        return pessoas.findById(id).orElseThrow(() -> new NaoEncontradoException("Pessoa", id));
    }
}
