package pt.karate.aprendizagem.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.karate.aprendizagem.domain.AlunoRef;
import pt.karate.aprendizagem.domain.NaoEncontradoException;
import pt.karate.aprendizagem.domain.Turma;
import pt.karate.aprendizagem.repo.AlunoRefRepository;
import pt.karate.aprendizagem.repo.TurmaRepository;

import java.util.UUID;

/** Comandos sobre turmas. */
@Service
public class TurmaService {

    public record DadosTurma(String nome, String descricao, String instrutor, boolean ativa) {
    }

    private final TurmaRepository turmas;
    private final AlunoRefRepository alunos;
    private final EventosAprendizagem eventos;

    public TurmaService(TurmaRepository turmas, AlunoRefRepository alunos, EventosAprendizagem eventos) {
        this.turmas = turmas;
        this.alunos = alunos;
        this.eventos = eventos;
    }

    @Transactional
    public ResultadoComando criar(DadosTurma d) {
        Turma t = new Turma(UUID.randomUUID(), d.nome(), d.descricao(), d.instrutor());
        if (!d.ativa()) {
            t.atualizar(d.nome(), d.descricao(), d.instrutor(), false);
        }
        turmas.save(t);
        return publicar(t);
    }

    @Transactional
    public ResultadoComando atualizar(UUID id, DadosTurma d) {
        Turma t = carregar(id);
        t.atualizar(d.nome(), d.descricao(), d.instrutor(), d.ativa());
        return publicar(t);
    }

    @Transactional
    public ResultadoComando adicionarAluno(UUID turmaId, UUID alunoId) {
        Turma t = carregar(turmaId);
        t.adicionarAluno(aluno(alunoId));
        return publicar(t);
    }

    @Transactional
    public ResultadoComando removerAluno(UUID turmaId, UUID alunoId) {
        Turma t = carregar(turmaId);
        t.removerAluno(alunoId);
        return publicar(t);
    }

    Turma carregar(UUID id) {
        return turmas.findById(id).orElseThrow(() -> new NaoEncontradoException("Turma", id));
    }

    private ResultadoComando publicar(Turma t) {
        return new ResultadoComando(t.getId().toString(), eventos.turmaAlterada(t));
    }

    private AlunoRef aluno(UUID id) {
        return alunos.findById(id).orElseThrow(() -> new NaoEncontradoException("Aluno", id));
    }
}
