package pt.karate.aprendizagem.app;

import pt.karate.aprendizagem.domain.AlunoRef;
import pt.karate.aprendizagem.domain.Aula;
import pt.karate.aprendizagem.domain.AvaliacaoTema;
import pt.karate.aprendizagem.domain.EstadoPresenca;
import pt.karate.aprendizagem.domain.ExameGraduacao;
import pt.karate.aprendizagem.domain.Graduacao;
import pt.karate.aprendizagem.domain.Presenca;
import pt.karate.aprendizagem.domain.RequisitosGraduacao.Elegibilidade;
import pt.karate.aprendizagem.domain.Tema;
import pt.karate.aprendizagem.domain.Turma;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Representações devolvidas pela API (construídas dentro da transação). */
public final class Vistas {

    private Vistas() {
    }

    public record GraduacaoView(String codigo, String nome, String corFaixa, String tipo, int ordem,
                                int presencasMinimas, int mesesMinimos, boolean ativa) {
    }

    public record TemaView(UUID id, String nome, String categoria, String descricao,
                           String graduacaoCodigo, String graduacaoNome, boolean ativo) {
    }

    public record TemaResumo(UUID id, String nome, String categoria) {
    }

    public record AlunoResumo(UUID id, String nome, String estado) {
    }

    public record TurmaView(UUID id, String nome, String descricao, String instrutor, boolean ativa,
                            List<AlunoResumo> alunos) {
    }

    public record ResumoPresencas(long presentes, long atrasados, long faltas, long faltasJustificadas) {
    }

    public record AulaView(UUID id, UUID turmaId, String turma, LocalDate data, LocalTime horaInicio,
                           int duracaoMinutos, String instrutor, String sumario, boolean cancelada,
                           List<TemaResumo> temas, ResumoPresencas presencas) {
    }

    /** Linha da folha de presenças: estado nulo = ainda não registado. */
    public record LinhaFolha(UUID alunoId, String nome, boolean membroTurma, String estado, String observacao) {
    }

    public record FolhaPresencas(UUID aulaId, LocalDate data, LocalTime horaInicio, String turma,
                                 boolean cancelada, List<LinhaFolha> alunos) {
    }

    public record AvaliacaoView(UUID id, UUID temaId, String tema, String categoria, String nivel,
                                LocalDate data, String avaliador, String observacao) {
    }

    public record ExameView(UUID id, String graduacaoCodigo, String graduacao, String corFaixa, LocalDate data,
                            String examinador, String resultado, String observacoes,
                            boolean requisitosDispensados, String motivoDispensa) {
    }

    public record ElegibilidadeView(String proximaGraduacao, String proximaGraduacaoNome, boolean elegivel,
                                    long presencas, int presencasMinimas, long mesesDecorridos, int mesesMinimos,
                                    int temasDominados, int temasPrograma, List<String> pendentes) {
    }

    public record GraduacaoResumo(String codigo, String nome, String corFaixa) {
    }

    public record ProgressoView(UUID alunoId, String nome, GraduacaoResumo graduacaoAtual,
                                LocalDate dataGraduacaoAtual, GraduacaoResumo proximaGraduacao,
                                long totalPresencas, long presencasDesdeUltimaGraduacao, LocalDate ultimaPresenca,
                                ElegibilidadeView elegibilidade, List<ExameView> exames,
                                List<AvaliacaoView> temas) {
    }

    /** Tema na perspetiva de um aluno: avaliação atual e prática nas aulas. */
    public record TemaAlunoView(UUID temaId, String nome, String categoria, String graduacaoCodigo,
                                String nivel, LocalDate dataAvaliacao, long vezesPraticado, LocalDate ultimaPratica) {
    }

    public static GraduacaoView de(Graduacao g) {
        return new GraduacaoView(g.getCodigo(), g.getNome(), g.getCorFaixa(), g.getTipo().name(), g.getOrdem(),
                g.getPresencasMinimas(), g.getMesesMinimos(), g.isAtiva());
    }

    public static GraduacaoResumo resumo(Graduacao g) {
        return new GraduacaoResumo(g.getCodigo(), g.getNome(), g.getCorFaixa());
    }

    public static TemaView de(Tema t) {
        return new TemaView(t.getId(), t.getNome(), t.getCategoria().name(), t.getDescricao(),
                t.getGraduacao().map(Graduacao::getCodigo).orElse(null),
                t.getGraduacao().map(Graduacao::getNome).orElse(null), t.isAtivo());
    }

    public static TemaResumo resumo(Tema t) {
        return new TemaResumo(t.getId(), t.getNome(), t.getCategoria().name());
    }

    public static AlunoResumo de(AlunoRef a) {
        return new AlunoResumo(a.getId(), a.getNome(), a.getEstado());
    }

    public static TurmaView de(Turma t, Collection<AlunoRef> alunos) {
        return new TurmaView(t.getId(), t.getNome(), t.getDescricao(), t.getInstrutor(), t.isAtiva(),
                alunos.stream().sorted(Comparator.comparing(AlunoRef::getNome)).map(Vistas::de).toList());
    }

    public static AulaView de(Aula a) {
        List<Presenca> ps = a.getPresencas();
        var resumo = new ResumoPresencas(
                contar(ps, EstadoPresenca.PRESENTE), contar(ps, EstadoPresenca.ATRASADO),
                contar(ps, EstadoPresenca.FALTA), contar(ps, EstadoPresenca.FALTA_JUSTIFICADA));
        return new AulaView(a.getId(), a.getTurma().map(Turma::getId).orElse(null),
                a.getTurma().map(Turma::getNome).orElse(null), a.getData(), a.getHoraInicio(),
                a.getDuracaoMinutos(), a.getInstrutor(), a.getSumario(), a.isCancelada(),
                a.getTemas().stream().sorted(Comparator.comparing(Tema::getNome)).map(Vistas::resumo).toList(),
                resumo);
    }

    public static AvaliacaoView de(AvaliacaoTema a) {
        return new AvaliacaoView(a.getId(), a.getTema().getId(), a.getTema().getNome(),
                a.getTema().getCategoria().name(), a.getNivel().name(), a.getData(), a.getAvaliador(),
                a.getObservacao());
    }

    public static ExameView de(ExameGraduacao e) {
        Graduacao g = e.getGraduacao();
        return new ExameView(e.getId(), g.getCodigo(), g.getNome(), g.getCorFaixa(), e.getData(), e.getExaminador(),
                e.getResultado().name(), e.getObservacoes(), e.isRequisitosDispensados(), e.getMotivoDispensa());
    }

    public static ElegibilidadeView de(Elegibilidade e) {
        return new ElegibilidadeView(
                e.proxima() == null ? null : e.proxima().codigo(),
                e.proxima() == null ? null : e.proxima().nome(),
                e.elegivel(), e.presencas(), e.presencasMinimas(), e.mesesDecorridos(), e.mesesMinimos(),
                e.temasDominados(), e.temasPrograma(), e.pendentes());
    }

    public static ProgressoView de(ProgressoService.Progresso p) {
        return new ProgressoView(p.aluno().getId(), p.aluno().getNome(),
                p.atual().map(e -> resumo(e.getGraduacao())).orElse(null),
                p.atual().map(ExameGraduacao::getData).orElse(null),
                p.proxima().map(Vistas::resumo).orElse(null),
                p.totalPresencas(), p.presencasDesdeUltimaGraduacao(), p.ultimaPresenca(),
                de(p.elegibilidade()),
                p.exames().stream().map(Vistas::de).toList(),
                p.avaliacoesAtuais().stream().map(Vistas::de).toList());
    }

    private static long contar(List<Presenca> ps, EstadoPresenca estado) {
        return ps.stream().filter(p -> p.getEstado() == estado).count();
    }
}
