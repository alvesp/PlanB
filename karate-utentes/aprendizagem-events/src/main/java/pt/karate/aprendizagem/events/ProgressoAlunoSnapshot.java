package pt.karate.aprendizagem.events;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Estado completo da aprendizagem de um aluno: graduação atual e seguinte, presenças,
 * histórico de graduações e última avaliação de cada tema.
 * Indexado no read-side com versionamento externo ({@code versao}).
 */
public record ProgressoAlunoSnapshot(
        UUID alunoId,
        long versao,
        String nome,
        GraduacaoResumo graduacaoAtual,
        LocalDate dataGraduacaoAtual,
        GraduacaoResumo proximaGraduacao,
        long totalPresencas,
        long presencasDesdeUltimaGraduacao,
        LocalDate ultimaPresenca,
        List<GraduacaoObtida> historico,
        List<TemaAvaliado> temas
) {

    public record GraduacaoResumo(String codigo, String nome, String corFaixa, int ordem) {
    }

    public record GraduacaoObtida(UUID exameId, String codigo, String nome, LocalDate data, String examinador) {
    }

    public record TemaAvaliado(UUID temaId, String nome, String categoria, String graduacaoCodigo,
                               String nivel, LocalDate data) {
    }
}
