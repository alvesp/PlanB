package pt.karate.aprendizagem.app;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pt.karate.aprendizagem.app.ProgressoService.Progresso;
import pt.karate.aprendizagem.domain.ExameGraduacao;
import pt.karate.aprendizagem.domain.Graduacao;
import pt.karate.aprendizagem.events.GraduacaoAtribuida;
import pt.karate.aprendizagem.events.ProgressoAlunoSnapshot;
import pt.karate.aprendizagem.events.TopicosAprendizagem;
import pt.karate.aprendizagem.repo.ProgressoVersaoRepository;
import pt.karate.outbox.OutboxWriter;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.UUID;

/**
 * Grava na outbox os eventos de aprendizagem, na transação da alteração que os originou.
 */
@Component
public class EventosAprendizagem {

    private final OutboxWriter outbox;
    private final ProgressoService progresso;
    private final ProgressoVersaoRepository versoes;
    private final Clock clock;

    public EventosAprendizagem(OutboxWriter outbox, ProgressoService progresso,
                               ProgressoVersaoRepository versoes, Clock clock) {
        this.outbox = outbox;
        this.progresso = progresso;
        this.versoes = versoes;
        this.clock = clock;
    }

    /** Publica um snapshot de progresso por aluno (cada um uma única vez). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void progressoAlterado(Collection<UUID> alunoIds) {
        for (UUID alunoId : new LinkedHashSet<>(alunoIds)) {
            // Incrementar primeiro: bloqueia o aluno e garante que o snapshot vê as alterações anteriores.
            long versao = versoes.proxima(alunoId);
            Progresso p = progresso.calcular(alunoId, LocalDate.now(clock));
            outbox.registar("ProgressoAluno", alunoId, versao, "ProgressoAlunoAtualizado",
                    TopicosAprendizagem.PROGRESSO, snapshot(p, versao));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void graduacaoAtribuida(ExameGraduacao exame) {
        Graduacao g = exame.getGraduacao();
        var evento = new GraduacaoAtribuida(exame.getId(), exame.getAlunoId(), g.getCodigo(), g.getNome(),
                g.getCorFaixa(), g.getOrdem(), exame.getData(), exame.getExaminador(),
                exame.isRequisitosDispensados());
        // Chave = aluno, para manter a ordem das graduações de cada aluno.
        outbox.registar("Aluno", exame.getAlunoId(), g.getOrdem(), "GraduacaoAtribuida",
                TopicosAprendizagem.GRADUACOES, evento);
    }

    private static ProgressoAlunoSnapshot snapshot(Progresso p, long versao) {
        var atual = p.atual().map(e -> resumo(e.getGraduacao())).orElse(null);
        var historico = p.aprovados().stream()
                .map(e -> new ProgressoAlunoSnapshot.GraduacaoObtida(e.getId(), e.getGraduacao().getCodigo(),
                        e.getGraduacao().getNome(), e.getData(), e.getExaminador()))
                .toList();
        var temas = p.avaliacoesAtuais().stream()
                .map(a -> new ProgressoAlunoSnapshot.TemaAvaliado(a.getTema().getId(), a.getTema().getNome(),
                        a.getTema().getCategoria().name(),
                        a.getTema().getGraduacao().map(Graduacao::getCodigo).orElse(null),
                        a.getNivel().name(), a.getData()))
                .toList();
        return new ProgressoAlunoSnapshot(
                p.aluno().getId(), versao, p.aluno().getNome(),
                atual, p.atual().map(ExameGraduacao::getData).orElse(null),
                p.proxima().map(EventosAprendizagem::resumo).orElse(null),
                p.totalPresencas(), p.presencasDesdeUltimaGraduacao(), p.ultimaPresenca(),
                historico, temas);
    }

    private static ProgressoAlunoSnapshot.GraduacaoResumo resumo(Graduacao g) {
        return new ProgressoAlunoSnapshot.GraduacaoResumo(g.getCodigo(), g.getNome(), g.getCorFaixa(), g.getOrdem());
    }
}
