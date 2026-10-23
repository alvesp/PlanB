package pt.karate.aprendizagem.app;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pt.karate.aprendizagem.app.ProgressoService.Progresso;
import pt.karate.aprendizagem.domain.Aula;
import pt.karate.aprendizagem.domain.ExameGraduacao;
import pt.karate.aprendizagem.domain.Graduacao;
import pt.karate.aprendizagem.domain.Tema;
import pt.karate.aprendizagem.domain.Turma;
import pt.karate.aprendizagem.events.AulaSnapshot;
import pt.karate.aprendizagem.events.GraduacaoAtribuida;
import pt.karate.aprendizagem.events.GraduacaoCatalogoSnapshot;
import pt.karate.aprendizagem.events.ProgressoAlunoSnapshot;
import pt.karate.aprendizagem.events.TemaSnapshot;
import pt.karate.aprendizagem.events.TopicosAprendizagem;
import pt.karate.aprendizagem.events.TurmaSnapshot;
import pt.karate.aprendizagem.repo.VersoesRepository;
import pt.karate.outbox.OutboxWriter;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Lado de escrita do CQRS: cada alteração grava na outbox, na mesma transação, o snapshot
 * dos agregados afetados. O read-side (utentes-query-service) é construído só a partir destes eventos.
 */
@Component
public class EventosAprendizagem {

    private final OutboxWriter outbox;
    private final ProgressoService progresso;
    private final VersoesRepository versoes;
    private final Clock clock;

    public EventosAprendizagem(OutboxWriter outbox, ProgressoService progresso,
                               VersoesRepository versoes, Clock clock) {
        this.outbox = outbox;
        this.progresso = progresso;
        this.versoes = versoes;
        this.clock = clock;
    }

    /**
     * Publica um snapshot de progresso por aluno (cada um uma única vez).
     *
     * @return versão publicada de cada aluno
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<UUID, Long> progressoAlterado(Collection<UUID> alunoIds) {
        Map<UUID, Long> publicadas = new LinkedHashMap<>();
        for (UUID alunoId : new LinkedHashSet<>(alunoIds)) {
            // Incrementar primeiro: bloqueia o aluno e garante que o snapshot vê as alterações anteriores.
            long versao = versoes.proxima("ProgressoAluno", alunoId);
            Progresso p = progresso.calcular(alunoId, LocalDate.now(clock));
            outbox.registar("ProgressoAluno", alunoId, versao, "ProgressoAlunoAtualizado",
                    TopicosAprendizagem.PROGRESSO, snapshot(p, versao));
            publicadas.put(alunoId, versao);
        }
        return publicadas;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long progressoAlterado(UUID alunoId) {
        return progressoAlterado(java.util.List.of(alunoId)).get(alunoId);
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

    @Transactional(propagation = Propagation.MANDATORY)
    public long graduacaoAlterada(Graduacao g) {
        long versao = versoes.proxima("Graduacao", g.getCodigo());
        var snapshot = new GraduacaoCatalogoSnapshot(g.getCodigo(), versao, g.getNome(), g.getCorFaixa(),
                g.getTipo().name(), g.getOrdem(), g.getPresencasMinimas(), g.getMesesMinimos(), g.isAtiva());
        outbox.registar("Graduacao", TopicosAprendizagem.chaveGraduacao(g.getCodigo()), versao,
                "GraduacaoCatalogoAtualizada", TopicosAprendizagem.CATALOGO_GRADUACOES, snapshot);
        return versao;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long temaAlterado(Tema t) {
        long versao = versoes.proxima("Tema", t.getId());
        var snapshot = new TemaSnapshot(t.getId(), versao, t.getNome(), t.getCategoria().name(), t.getDescricao(),
                t.getGraduacao().map(Graduacao::getCodigo).orElse(null), t.isAtivo());
        outbox.registar("Tema", t.getId(), versao, "TemaAtualizado", TopicosAprendizagem.TEMAS, snapshot);
        return versao;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long turmaAlterada(Turma t) {
        long versao = versoes.proxima("Turma", t.getId());
        var snapshot = new TurmaSnapshot(t.getId(), versao, t.getNome(), t.getDescricao(), t.getInstrutor(),
                t.isAtiva(), t.getAlunos().stream().sorted().toList());
        outbox.registar("Turma", t.getId(), versao, "TurmaAtualizada", TopicosAprendizagem.TURMAS, snapshot);
        return versao;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long aulaAlterada(Aula a) {
        long versao = versoes.proxima("Aula", a.getId());
        var temas = a.getTemas().stream()
                .sorted(Comparator.comparing(Tema::getNome))
                .map(t -> new AulaSnapshot.TemaResumo(t.getId(), t.getNome(), t.getCategoria().name()))
                .toList();
        var presencas = a.getPresencas().stream()
                .map(p -> new AulaSnapshot.PresencaRegistada(p.getAlunoId(), p.getEstado().name(), p.getObservacao()))
                .toList();
        var snapshot = new AulaSnapshot(a.getId(), versao, a.getTurma().map(Turma::getId).orElse(null),
                a.getTurma().map(Turma::getNome).orElse(null), a.getData(), a.getHoraInicio(), a.getDuracaoMinutos(),
                a.getInstrutor(), a.getSumario(), a.isCancelada(), temas, presencas);
        outbox.registar("Aula", a.getId(), versao, "AulaAtualizada", TopicosAprendizagem.AULAS, snapshot);
        return versao;
    }

    private static ProgressoAlunoSnapshot snapshot(Progresso p, long versao) {
        var atual = p.atual().map(e -> resumo(e.getGraduacao())).orElse(null);
        var historico = p.aprovados().stream()
                .map(e -> new ProgressoAlunoSnapshot.GraduacaoObtida(e.getId(), e.getGraduacao().getCodigo(),
                        e.getGraduacao().getNome(), e.getData(), e.getExaminador()))
                .toList();
        var exames = p.exames().stream()
                .map(e -> new ProgressoAlunoSnapshot.Exame(e.getId(), e.getGraduacao().getCodigo(),
                        e.getGraduacao().getNome(), e.getData(), e.getExaminador(), e.getResultado().name(),
                        e.getObservacoes(), e.isRequisitosDispensados(), e.getMotivoDispensa()))
                .toList();
        var temas = p.avaliacoesAtuais().stream()
                .map(a -> new ProgressoAlunoSnapshot.TemaAvaliado(a.getTema().getId(), a.getTema().getNome(),
                        a.getTema().getCategoria().name(),
                        a.getTema().getGraduacao().map(Graduacao::getCodigo).orElse(null),
                        a.getNivel().name(), a.getData()))
                .toList();
        var avaliacoes = p.avaliacoesHistorico().stream()
                .map(a -> new ProgressoAlunoSnapshot.Avaliacao(a.getId(), a.getTema().getId(), a.getTema().getNome(),
                        a.getNivel().name(), a.getData(), a.getAvaliador(), a.getObservacao()))
                .toList();
        var pratica = p.pratica().stream()
                .map(pr -> new ProgressoAlunoSnapshot.Pratica(pr.temaId(), pr.vezes(), pr.ultimaVez()))
                .toList();
        var requisitos = p.proxima().map(g -> new ProgressoAlunoSnapshot.Requisitos(
                        g.getPresencasMinimas(), g.getMesesMinimos(), p.referencia(),
                        p.referencia() == null ? null : p.referencia().plusMonths(g.getMesesMinimos()),
                        p.elegibilidade().temasPrograma(), p.elegibilidade().temasDominados()))
                .orElse(null);
        return new ProgressoAlunoSnapshot(
                p.aluno().getId(), versao, p.aluno().getNome(), p.aluno().getDataInscricao(),
                atual, p.atual().map(ExameGraduacao::getData).orElse(null),
                p.proxima().map(EventosAprendizagem::resumo).orElse(null),
                p.totalPresencas(), p.presencasDesdeUltimaGraduacao(), p.ultimaPresenca(),
                requisitos, historico, exames, temas, avaliacoes, pratica);
    }

    private static ProgressoAlunoSnapshot.GraduacaoResumo resumo(Graduacao g) {
        return new ProgressoAlunoSnapshot.GraduacaoResumo(g.getCodigo(), g.getNome(), g.getCorFaixa(), g.getOrdem());
    }
}
