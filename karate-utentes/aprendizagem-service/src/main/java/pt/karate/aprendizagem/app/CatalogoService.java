package pt.karate.aprendizagem.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.karate.aprendizagem.app.Vistas.GraduacaoView;
import pt.karate.aprendizagem.app.Vistas.TemaView;
import pt.karate.aprendizagem.domain.CategoriaTema;
import pt.karate.aprendizagem.domain.Graduacao;
import pt.karate.aprendizagem.domain.NaoEncontradoException;
import pt.karate.aprendizagem.domain.RegraNegocioException;
import pt.karate.aprendizagem.domain.Tema;
import pt.karate.aprendizagem.domain.TipoGraduacao;
import pt.karate.aprendizagem.repo.GraduacaoRepository;
import pt.karate.aprendizagem.repo.TemaRepository;

import java.util.List;
import java.util.UUID;

/** Catálogo de graduações e temas (o "currículo" da escola). */
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

    public CatalogoService(GraduacaoRepository graduacoes, TemaRepository temas) {
        this.graduacoes = graduacoes;
        this.temas = temas;
    }

    @Transactional(readOnly = true)
    public List<GraduacaoView> graduacoes() {
        return graduacoes.findAllByOrderByOrdemAsc().stream().map(Vistas::de).toList();
    }

    @Transactional
    public GraduacaoView criarGraduacao(String codigo, DadosGraduacao d) {
        if (graduacoes.existsById(codigo)) {
            throw new RegraNegocioException("GRADUACAO_EXISTENTE", "Já existe a graduação " + codigo + ".");
        }
        garantirOrdemLivre(d.ordem(), codigo);
        Graduacao g = new Graduacao(codigo, d.nome(), d.corFaixa(), d.tipo(), d.ordem(),
                d.presencasMinimas(), d.mesesMinimos());
        if (!d.ativa()) {
            g.atualizar(d.nome(), d.corFaixa(), d.tipo(), d.ordem(), d.presencasMinimas(), d.mesesMinimos(), false);
        }
        return Vistas.de(graduacoes.save(g));
    }

    /**
     * Atualiza uma graduação. Não há remoção: graduações já usadas em exames desativam-se.
     * Mudar a ordem pode alterar a "próxima graduação" dos alunos; use com cuidado.
     */
    @Transactional
    public GraduacaoView atualizarGraduacao(String codigo, DadosGraduacao d) {
        Graduacao g = graduacao(codigo);
        garantirOrdemLivre(d.ordem(), codigo);
        g.atualizar(d.nome(), d.corFaixa(), d.tipo(), d.ordem(), d.presencasMinimas(), d.mesesMinimos(), d.ativa());
        return Vistas.de(g);
    }

    @Transactional(readOnly = true)
    public List<TemaView> programa(String codigoGraduacao) {
        graduacao(codigoGraduacao);
        return temas.programa(codigoGraduacao).stream().map(Vistas::de).toList();
    }

    @Transactional(readOnly = true)
    public List<TemaView> pesquisarTemas(CategoriaTema categoria, String graduacao, boolean incluirInativos) {
        return temas.pesquisar(categoria, graduacao, incluirInativos).stream().map(Vistas::de).toList();
    }

    @Transactional(readOnly = true)
    public TemaView tema(UUID id) {
        return Vistas.de(carregarTema(id));
    }

    @Transactional
    public TemaView criarTema(DadosTema d) {
        UUID id = UUID.randomUUID();
        garantirNomeLivre(d.nome(), id);
        Tema t = new Tema(id, d.nome(), d.categoria(), d.descricao(), graduacaoOuNull(d.graduacaoCodigo()));
        if (!d.ativo()) {
            t.atualizar(d.nome(), d.categoria(), d.descricao(), graduacaoOuNull(d.graduacaoCodigo()), false);
        }
        return Vistas.de(temas.save(t));
    }

    @Transactional
    public TemaView atualizarTema(UUID id, DadosTema d) {
        Tema t = carregarTema(id);
        garantirNomeLivre(d.nome(), id);
        t.atualizar(d.nome(), d.categoria(), d.descricao(), graduacaoOuNull(d.graduacaoCodigo()), d.ativo());
        return Vistas.de(t);
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
        if (graduacoes.existsByOrdemAndCodigoNot(ordem, codigo)) {
            throw new RegraNegocioException("ORDEM_EM_USO", "Já existe outra graduação com a ordem " + ordem + ".");
        }
    }

    private void garantirNomeLivre(String nome, UUID id) {
        if (nome != null && temas.nomeEmUso(nome.strip(), id)) {
            throw new RegraNegocioException("TEMA_EXISTENTE", "Já existe um tema com o nome " + nome + ".");
        }
    }
}
