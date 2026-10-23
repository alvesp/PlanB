package pt.karate.aprendizagem.events;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Estado completo da aprendizagem de um aluno. Indexado no read-side com versionamento
 * externo ({@code versao}); serve progresso, exames, avaliações, prática e elegibilidade.
 *
 * @param historico   graduações obtidas (exames aprovados)
 * @param exames      todos os exames, incluindo reprovações
 * @param temas       avaliação atual (mais recente) de cada tema
 * @param avaliacoes  histórico completo de avaliações
 * @param pratica     presenças em aulas onde cada tema foi trabalhado
 */
public record ProgressoAlunoSnapshot(
        UUID alunoId,
        long versao,
        String nome,
        LocalDate dataInscricao,
        GraduacaoResumo graduacaoAtual,
        LocalDate dataGraduacaoAtual,
        GraduacaoResumo proximaGraduacao,
        long totalPresencas,
        long presencasDesdeUltimaGraduacao,
        LocalDate ultimaPresenca,
        Requisitos requisitos,
        List<GraduacaoObtida> historico,
        List<Exame> exames,
        List<TemaAvaliado> temas,
        List<Avaliacao> avaliacoes,
        List<Pratica> pratica
) {

    public record GraduacaoResumo(String codigo, String nome, String corFaixa, int ordem) {
    }

    public record GraduacaoObtida(UUID exameId, String codigo, String nome, LocalDate data, String examinador) {
    }

    public record Exame(UUID exameId, String codigo, String nome, LocalDate data, String examinador,
                        String resultado, String observacoes, boolean requisitosDispensados, String motivoDispensa) {
    }

    public record TemaAvaliado(UUID temaId, String nome, String categoria, String graduacaoCodigo,
                               String nivel, LocalDate data) {
    }

    public record Avaliacao(UUID avaliacaoId, UUID temaId, String tema, String nivel, LocalDate data,
                            String avaliador, String observacao) {
    }

    public record Pratica(UUID temaId, long vezes, LocalDate ultimaVez) {
    }

    /**
     * Requisitos para a próxima graduação. O read-side calcula a elegibilidade com a data de
     * hoje: presenças desde a última graduação ≥ {@code presencasMinimas} e hoje ≥
     * {@code cumpreTempoEm}. Nulo se não houver graduação seguinte.
     */
    public record Requisitos(int presencasMinimas, int mesesMinimos, LocalDate referencia, LocalDate cumpreTempoEm,
                             int temasPrograma, int temasDominados) {
    }
}
