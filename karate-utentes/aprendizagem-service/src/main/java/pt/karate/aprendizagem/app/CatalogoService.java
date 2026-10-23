package pt.karate.aprendizagem.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.karate.aprendizagem.domain.CategoriaTema;
import pt.karate.aprendizagem.domain.Graduacao;
import pt.karate.aprendizagem.domain.NaoEncontradoException;
import pt.karate.aprendizagem.domain.RegraNegocioException;
import pt.karate.aprendizagem.domain.Tema;
import pt.karate.aprendizagem.domain.TipoGraduacao;
import pt.karate.aprendizagem.repo.AlunoRefRepository;
import pt.karate.aprendizagem.repo.AulaRepository;
import pt.karate.aprendizagem.repo.GraduacaoRepository;
import pt.karate.aprendizagem.repo.TemaRepository;

import java.util.UUID;

/** Comandos do catálogo de graduações e temas (o "currículo" da escola). */
@Service
public class CatalogoService {

    public record DadosGraduacao(String nome, String corFaixa, TipoGraduacao tipo, int ordem,
                                 int presencasMinimas, int mesesMinimos, boolean ativa) {
    }

    public record DadosTema(String nome, CategoriaTema categoria, String descricao, String graduacaoCodigo,
                            boolean ativo) {
    }

    private final GraduacaoRepository graduacoes;
    private final TemaRepository temas;
    private final AlunoRefRepository alunos;
    private final AulaRepository aulas;
    private final EventosAprendizagem eventos;

    public CatalogoService(GraduacaoRepository graduacoes, TemaRepository temas, AlunoRefRepository alunos,
                           AulaRepository aulas, EventosAprendizagem eventos) {
        this.graduacoes = graduacoes;
        this.temas = temas;
        this.alunos = alunos;
        this.aulas = aulas;
        this.eventos = eventos;
    }

    @Transactional
    public ResultadoComando criarGraduacao(String codigo, DadosGraduacao d) {
        if (codigo != null && graduacoes.existsById(codigo)) {
            throw new RegraNegocioException("GRADUACAO_EXISTENTE", "Já existe a graduação " + codigo + ".");
        }
        garantirOrdemLivre(d.ordem(), codigo);
        Graduacao g = new Graduacao(codigo, d.nome(), d.corFaixa(), d.tipo(), d.ordem(),
                d.presencasMinimas(), d.mesesMinimos());
        if (!d.ativa()) {
            g.atualizar(d.nome(), d.corFaixa(), d.tipo(), d.ordem(), d.presencasMinimas(), d.mesesMinimos(), false);
        }
        graduacoes.save(g);
        long versao = eventos.graduacaoAlterada(g);
        // Pode mudar a "próxima graduação" de qualquer aluno.
        eventos.progressoAlterado(alunos.todosOsIds());
        return new ResultadoComando(g.getCodigo(), versao);
    }

    /**
     * Atualiza uma graduação. Não há remoção: graduações já usadas desativam-se.
     * Como a ordem e os mínimos afetam a progressão de todos, republica o progresso de todos os alunos.
     */
    @Transactional
    public ResultadoComando atualizarGraduacao(String codigo, DadosGraduacao d) {
        Graduacao g = graduacao(codigo);
        garantirOrdemLivre(d.ordem(), codigo);
        g.atualizar(d.nome(), d.corFaixa(), d.tipo(), d.ordem(), d.presencasMinimas(), d.mesesMinimos(), d.ativa());
        long versao = eventos.graduacaoAlterada(g);
        eventos.progressoAlterado(alunos.todosOsIds());
        return new ResultadoComando(codigo, versao);
    }

    @Transactional
    public ResultadoComando criarTema(DadosTema d) {
        UUID id = UUID.randomUUID();
        garantirNomeLivre(d.nome(), id);
        Tema t = new Tema(id, d.nome(), d.categoria(), d.descricao(), graduacaoOuNull(d.graduacaoCodigo()));
        if (!d.ativo()) {
            t.atualizar(d.nome(), d.categoria(), d.descricao(), graduacaoOuNull(d.graduacaoCodigo()), false);
        }
        temas.save(t);
        long versao = eventos.temaAlterado(t);
        if (t.getGraduacao().isPresent()) {
            // Muda o programa (e os temas por dominar) de quem se prepara para essa graduação.
            eventos.progressoAlterado(alunos.todosOsIds());
        }
        return new ResultadoComando(id.toString(), versao);
    }

    /** Republica também as aulas e o progresso dos alunos que mostram o nome ou o programa do tema. */
    @Transactional
    public ResultadoComando atualizarTema(UUID id, DadosTema d) {
        Tema t = carregarTema(id);
        garantirNomeLivre(d.nome(), id);
        t.atualizar(d.nome(), d.categoria(), d.descricao(), graduacaoOuNull(d.graduacaoCodigo()), d.ativo());
        long versao = eventos.temaAlterado(t);
        aulas.comTema(id).forEach(eventos::aulaAlterada);
        eventos.progressoAlterado(alunos.todosOsIds());
        return new ResultadoComando(id.toString(), versao);
    }

    Tema carregarTema(UUID id) {
        return temas.findById(id).orElseThrow(() -> new NaoEncontradoException("Tema", id));
    }

    Graduacao graduacao(String codigo) {
        return graduacoes.findById(codigo).orElseThrow(() -> new NaoEncontradoException("Graduação", codigo));
    }

    private Graduacao graduacaoOuNull(String codigo) {
        return codigo == null || codigo.isBlank() ? null : graduacao(codigo);
    }

    private void garantirOrdemLivre(int ordem, String codigo) {
        if (graduacoes.existsByOrdemAndCodigoNot(ordem, codigo == null ? "" : codigo)) {
            throw new RegraNegocioException("ORDEM_EM_USO", "Já existe outra graduação com a ordem " + ordem + ".");
        }
    }

    private void garantirNomeLivre(String nome, UUID id) {
        if (nome != null && temas.nomeEmUso(nome.strip(), id)) {
            throw new RegraNegocioException("TEMA_EXISTENTE", "Já existe um tema com o nome " + nome + ".");
        }
    }
}
