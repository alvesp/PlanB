package pt.karate.aprendizagem.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.karate.aprendizagem.app.ProgressoService.Progresso;
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

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Comandos sobre a aprendizagem de um aluno: avaliações de temas e exames de graduação.
 * O progresso, a elegibilidade, as presenças e o histórico leem-se no read-side.
 */
@Service
public class AlunoAprendizagemService {

    public record NovaAvaliacao(UUID temaId, NivelDominio nivel, LocalDate data, String avaliador, String observacao) {
    }

    public record NovoExame(String graduacaoCodigo, LocalDate data, String examinador, ResultadoExame resultado,
                            String observacoes, boolean dispensarRequisitos, String motivoDispensa) {
    }

    private final AlunoRefRepository alunos;
    private final ProgressoService progresso;
    private final AvaliacaoTemaRepository avaliacoes;
    private final ExameRepository exames;
    private final CatalogoService catalogo;
    private final EventosAprendizagem eventos;
    private final Clock clock;

    public AlunoAprendizagemService(AlunoRefRepository alunos, ProgressoService progresso,
                                    AvaliacaoTemaRepository avaliacoes, ExameRepository exames,
                                    CatalogoService catalogo, EventosAprendizagem eventos, Clock clock) {
        this.alunos = alunos;
        this.progresso = progresso;
        this.avaliacoes = avaliacoes;
        this.exames = exames;
        this.catalogo = catalogo;
        this.eventos = eventos;
        this.clock = clock;
    }

    /** @return id da avaliação e versão do progresso do aluno a esperar no read-side */
    @Transactional
    public ResultadoComando avaliar(UUID alunoId, NovaAvaliacao cmd) {
        AlunoRef aluno = aluno(alunoId);
        Tema tema = catalogo.carregarTema(cmd.temaId());
        LocalDate data = cmd.data() != null ? cmd.data() : hoje();
        AvaliacaoTema avaliacao = avaliacoes.save(
                new AvaliacaoTema(aluno, tema, cmd.nivel(), data, cmd.avaliador(), cmd.observacao(), clock));
        long versao = eventos.progressoAlterado(alunoId);
        return new ResultadoComando(avaliacao.getId().toString(), versao);
    }

    /**
     * Regista um exame. Se aprovado, a graduação passa a ser a atual do aluno e é publicado
     * {@code GraduacaoAtribuida} (o utentes-service atualiza a ficha do aluno).
     *
     * @return id do exame e versão do progresso do aluno a esperar no read-side
     */
    @Transactional
    public ResultadoComando registarExame(UUID alunoId, NovoExame cmd) {
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
        long versao = eventos.progressoAlterado(alunoId);
        return new ResultadoComando(exame.getId().toString(), versao);
    }

    private AlunoRef aluno(UUID id) {
        return alunos.findById(id).orElseThrow(() -> new NaoEncontradoException("Aluno", id));
    }

    private LocalDate hoje() {
        return LocalDate.now(clock);
    }
}
