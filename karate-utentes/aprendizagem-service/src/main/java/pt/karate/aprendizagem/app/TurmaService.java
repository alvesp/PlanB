package pt.karate.aprendizagem.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.karate.aprendizagem.app.Vistas.TurmaView;
import pt.karate.aprendizagem.domain.AlunoRef;
import pt.karate.aprendizagem.domain.NaoEncontradoException;
import pt.karate.aprendizagem.domain.Turma;
import pt.karate.aprendizagem.repo.AlunoRefRepository;
import pt.karate.aprendizagem.repo.TurmaRepository;

import java.util.List;
import java.util.UUID;

@Service
public class TurmaService {

    public record DadosTurma(String nome, String descricao, String instrutor, boolean ativa) {
    }

    private final TurmaRepository turmas;
    private final AlunoRefRepository alunos;

    public TurmaService(TurmaRepository turmas, AlunoRefRepository alunos) {
        this.turmas = turmas;
        this.alunos = alunos;
    }

    @Transactional(readOnly = true)
    public List<TurmaView> listar() {
        return turmas.findAllByOrderByNomeAsc().stream().map(this::vista).toList();
    }

    @Transactional(readOnly = true)
    public TurmaView obter(UUID id) {
        return vista(carregar(id));
    }

    @Transactional
    public TurmaView criar(DadosTurma d) {
        Turma t = new Turma(UUID.randomUUID(), d.nome(), d.descricao(), d.instrutor());
        return vista(turmas.save(t));
    }

    @Transactional
    public TurmaView atualizar(UUID id, DadosTurma d) {
        Turma t = carregar(id);
        t.atualizar(d.nome(), d.descricao(), d.instrutor(), d.ativa());
        return vista(t);
    }

    @Transactional
    public TurmaView adicionarAluno(UUID turmaId, UUID alunoId) {
        Turma t = carregar(turmaId);
        t.adicionarAluno(aluno(alunoId));
        return vista(t);
    }

    @Transactional
    public TurmaView removerAluno(UUID turmaId, UUID alunoId) {
        Turma t = carregar(turmaId);
        t.removerAluno(alunoId);
        return vista(t);
    }

    Turma carregar(UUID id) {
        return turmas.findById(id).orElseThrow(() -> new NaoEncontradoException("Turma", id));
    }

    private AlunoRef aluno(UUID id) {
        return alunos.findById(id).orElseThrow(() -> new NaoEncontradoException("Aluno", id));
    }

    private TurmaView vista(Turma t) {
        return Vistas.de(t, alunos.findAllById(t.getAlunos()));
    }
}
