package pt.karate.aprendizagem.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.karate.aprendizagem.app.ProgressoService.Progresso;
import pt.karate.aprendizagem.app.Vistas.AvaliacaoView;
import pt.karate.aprendizagem.app.Vistas.ElegibilidadeView;
import pt.karate.aprendizagem.app.Vistas.ExameView;
import pt.karate.aprendizagem.app.Vistas.ProgressoView;
import pt.karate.aprendizagem.app.Vistas.TemaAlunoView;
import pt.karate.aprendizagem.domain.AlunoRef;
import pt.karate.aprendizagem.domain.AvaliacaoTema;
import pt.karate.aprendizagem.domain.ExameGraduacao;
import pt.karate.aprendizagem.domain.Graduacao;
import pt.karate.aprendizagem.domain.NaoEncontradoException;
import pt.karate.aprendizagem.domain.NivelDominio;
import pt.karate.aprendizagem.domain.RegraNegocioException;
import pt.karate.aprendizagem.domain.RequisitosGraduacao;
import pt.karate.aprendizagem.domain.ResultadoExame;
import pt.karate.aprendizagem.domain.Tema;
import pt.karate.aprendizagem.repo.AlunoRefRepository;
import pt.karate.aprendizagem.repo.AvaliacaoTemaRepository;
import pt.karate.aprendizagem.repo.ExameRepository;
import pt.karate.aprendizagem.repo.PraticaTema;
import pt.karate.aprendizagem.repo.PresencaRepository;
import pt.karate.aprendizagem.repo.RegistoPresenca;
import pt.karate.aprendizagem.repo.TemaRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Aprendizagem de cada aluno: progresso, presenças, temas, avaliações e exames. */
@Service
public class AlunoAprendizagemService {

    public record NovaAvaliacao(UUID temaId, NivelDominio nivel, LocalDate data, String avaliador, String observacao) {
    }

    public record NovoExame(String graduacaoCodigo, LocalDate data, String examinador, ResultadoExame resultado,
                            String observacoes, boolean dispensarRequisitos, String motivoDispensa) {
    }

    private final AlunoRefRepository alunos;
    private final ProgressoService progresso;
    private final PresencaRepository presencas;
    private final AvaliacaoTemaRepository avaliacoes;
    private final ExameRepository exames;
    private final TemaRepository temas;
    private final CatalogoService catalogo;
    private final EventosAprendizagem eventos;
    private final Clock clock;

    public AlunoAprendizagemService(AlunoRefRepository alunos, ProgressoService progresso, PresencaRepository presencas,
                                    AvaliacaoTemaRepository avaliacoes, ExameRepository exames, TemaRepository temas,
                                    CatalogoService catalogo, EventosAprendizagem eventos, Clock clock) {
        this.alunos = alunos;
        this.progresso = progresso;
        this.presencas = presencas;
        this.avaliacoes = avaliacoes;
        this.exames = exames;
        this.temas = temas;
        this.catalogo = catalogo;
        this.eventos = eventos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProgressoView progresso(UUID alunoId) {
        return Vistas.de(progresso.calcular(alunoId, hoje()));
    }

    @Transactional(readOnly = true)
    public ElegibilidadeView elegibilidade(UUID alunoId) {
        return Vistas.de(progresso.calcular(alunoId, hoje()).elegibilidade());
    }

    @Transactional(readOnly = true)
    public List<RegistoPresenca> presencas(UUID alunoId, LocalDate de, LocalDate ate) {
        aluno(alunoId);
        LocalDate fim = ate != null ? ate : hoje();
        LocalDate inicio = de != null ? de : fim.minusMonths(3);
        return presencas.registosDoAluno(alunoId, inicio, fim);
    }

    /**
     * Temas na perspetiva do aluno: por omissão o programa da próxima graduação; com
     * {@code todos=true}, todos os temas ativos. Inclui o nível da última avaliação e
     * quantas vezes treinou cada tema nas aulas em que esteve presente.
     */
    @Transactional(readOnly = true)
    public List<TemaAlunoView> temas(UUID alunoId, boolean todos) {
        Progresso p = progresso.calcular(alunoId, hoje());
        List<Tema> lista = todos ? temas.pesquisar(null, null, false) : p.programaProxima();

        Map<UUID, AvaliacaoTema> avaliacaoPorTema = p.avaliacoesAtuais().stream()
                .collect(Collectors.toMap(a -> a.getTema().getId(), Function.identity(), (a, b) -> a));
        Map<UUID, PraticaTema> praticaPorTema = presencas.praticaPorTema(alunoId).stream()
                .collect(Collectors.toMap(PraticaTema::temaId, Function.identity()));

        return lista.stream().map(t -> {
            AvaliacaoTema a = avaliacaoPorTema.get(t.getId());
            PraticaTema pr = praticaPorTema.get(t.getId());
            return new TemaAlunoView(t.getId(), t.getNome(), t.getCategoria().name(),
                    t.getGraduacao().map(Graduacao::getCodigo).orElse(null),
                    a == null ? null : a.getNivel().name(), a == null ? null : a.getData(),
                    pr == null ? 0 : pr.vezes(), pr == null ? null : pr.ultimaVez());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<AvaliacaoView> avaliacoes(UUID alunoId) {
        aluno(alunoId);
        return avaliacoes.historico(alunoId).stream().map(Vistas::de).toList();
    }

    @Transactional
    public AvaliacaoView avaliar(UUID alunoId, NovaAvaliacao cmd) {
        AlunoRef aluno = aluno(alunoId);
        Tema tema = catalogo.carregarTema(cmd.temaId());
        LocalDate data = cmd.data() != null ? cmd.data() : hoje();
        AvaliacaoTema avaliacao = avaliacoes.save(
                new AvaliacaoTema(aluno, tema, cmd.nivel(), data, cmd.avaliador(), cmd.observacao(), clock));
        eventos.progressoAlterado(List.of(alunoId));
        return Vistas.de(avaliacao);
    }

    @Transactional(readOnly = true)
    public List<ExameView> exames(UUID alunoId) {
        aluno(alunoId);
        return exames.historico(alunoId).stream().map(Vistas::de).toList();
    }

    /**
     * Regista um exame. Se aprovado, a graduação passa a ser a atual do aluno e é publicado
     * {@code GraduacaoAtribuida} (o utentes-service atualiza a ficha do aluno).
     */
    @Transactional
    public ExameView registarExame(UUID alunoId, NovoExame cmd) {
        AlunoRef aluno = aluno(alunoId);
        Graduacao alvo = catalogo.graduacao(cmd.graduacaoCodigo());
        if (!alvo.isAtiva()) {
            throw new RegraNegocioException("GRADUACAO_INATIVA", "A graduação " + alvo.getNome() + " está inativa.");
        }
        LocalDate data = cmd.data() != null ? cmd.data() : hoje();

        // Requisitos avaliados à data do exame (permite registar exames passados).
        Progresso p = progresso.calcular(alunoId, data);
        RequisitosGraduacao.validarExame(
                p.atual().map(e -> e.getGraduacao().ref()).orElse(null),
                p.atual().map(ExameGraduacao::getData).orElse(null),
                alvo.ref(), data, hoje(), p.elegibilidade(),
                cmd.dispensarRequisitos(), cmd.motivoDispensa());

        ExameGraduacao exame = exames.save(new ExameGraduacao(aluno, alvo, data, cmd.examinador(), cmd.resultado(),
                cmd.observacoes(), cmd.dispensarRequisitos(), cmd.motivoDispensa(), clock));
        if (exame.aprovado()) {
            eventos.graduacaoAtribuida(exame);
        }
        eventos.progressoAlterado(List.of(alunoId));
        return Vistas.de(exame);
    }

    private AlunoRef aluno(UUID id) {
        return alunos.findById(id).orElseThrow(() -> new NaoEncontradoException("Aluno", id));
    }

    private LocalDate hoje() {
        return LocalDate.now(clock);
    }
}
