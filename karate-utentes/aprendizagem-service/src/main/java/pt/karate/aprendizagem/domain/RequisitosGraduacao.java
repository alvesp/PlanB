package pt.karate.aprendizagem.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Regras de progressão entre graduações, sem dependências de framework.
 *
 * <h2>Elegibilidade para exame</h2>
 * Para se apresentar a exame da graduação seguinte, o aluno precisa de:
 * <ul>
 *   <li>pelo menos {@code presencasMinimas} presenças desde a última graduação;</li>
 *   <li>pelo menos {@code mesesMinimos} meses desde a última graduação
 *       (ou desde a inscrição, se ainda não tiver nenhuma).</li>
 * </ul>
 * O domínio dos temas do programa é informativo: é avaliado no próprio exame.
 *
 * <h2>Registo de exame</h2>
 * Só se pode registar exame à graduação seguinte, com os requisitos cumpridos, numa data
 * não futura e posterior à graduação atual. Com dispensa de requisitos (ex.: aluno vindo de
 * outro clube) é permitido saltar graduações e ignorar os mínimos, mas o motivo é obrigatório.
 */
public final class RequisitosGraduacao {

    /** Vista mínima de uma graduação do catálogo. */
    public record GraduacaoRef(String codigo, String nome, int ordem, int presencasMinimas, int mesesMinimos) {
    }

    public record Elegibilidade(
            GraduacaoRef proxima,
            long presencas,
            int presencasMinimas,
            long mesesDecorridos,
            int mesesMinimos,
            int temasDominados,
            int temasPrograma,
            List<String> pendentes
    ) {
        public boolean elegivel() {
            return proxima != null && pendentes.isEmpty();
        }
    }

    private RequisitosGraduacao() {
    }

    /**
     * @param proxima        graduação seguinte (null se o aluno já tem a mais alta)
     * @param referencia     data da última graduação, ou da inscrição se ainda não tiver
     * @param presencasDesde presenças contadas desde {@code referencia}
     * @param ate            data em que se avalia (hoje, ou a data do exame)
     */
    public static Elegibilidade avaliar(GraduacaoRef proxima, LocalDate referencia, long presencasDesde,
                                        int temasPrograma, int temasDominados, LocalDate ate) {
        if (proxima == null) {
            return new Elegibilidade(null, presencasDesde, 0, 0, 0, temasDominados, temasPrograma,
                    List.of("Não existe graduação seguinte no catálogo."));
        }
        long meses = referencia == null ? 0 : Math.max(0, ChronoUnit.MONTHS.between(referencia, ate));

        List<String> pendentes = new ArrayList<>();
        if (presencasDesde < proxima.presencasMinimas()) {
            pendentes.add("Presenças: " + presencasDesde + " de " + proxima.presencasMinimas() + " necessárias.");
        }
        if (meses < proxima.mesesMinimos()) {
            pendentes.add("Tempo: " + meses + " de " + proxima.mesesMinimos() + " meses necessários.");
        }
        return new Elegibilidade(proxima, presencasDesde, proxima.presencasMinimas(), meses,
                proxima.mesesMinimos(), temasDominados, temasPrograma, List.copyOf(pendentes));
    }

    /**
     * Valida o registo de um exame. Lança {@link RegraNegocioException} na primeira regra violada.
     *
     * @param atual          graduação atual do aluno (null se nenhuma)
     * @param dataAtual      data em que obteve a graduação atual (null se nenhuma)
     * @param alvo           graduação a que se apresenta
     * @param elegibilidade  avaliada à data do exame
     */
    public static void validarExame(GraduacaoRef atual, LocalDate dataAtual, GraduacaoRef alvo,
                                    LocalDate dataExame, LocalDate hoje, Elegibilidade elegibilidade,
                                    boolean dispensarRequisitos, String motivoDispensa) {
        Objects.requireNonNull(alvo, "alvo");
        Objects.requireNonNull(dataExame, "dataExame");

        if (dataExame.isAfter(hoje)) {
            throw new RegraNegocioException("DATA_EXAME_FUTURA", "A data do exame não pode ser futura.");
        }
        if (atual != null && alvo.ordem() <= atual.ordem()) {
            throw new RegraNegocioException("GRADUACAO_NAO_SUPERIOR",
                    "O aluno já tem " + atual.nome() + "; o exame tem de ser a uma graduação superior.");
        }
        if (dataAtual != null && dataExame.isBefore(dataAtual)) {
            throw new RegraNegocioException("DATA_ANTERIOR_A_GRADUACAO_ATUAL",
                    "A data do exame é anterior à da graduação atual (" + dataAtual + ").");
        }
        if (dispensarRequisitos) {
            if (motivoDispensa == null || motivoDispensa.isBlank()) {
                throw new RegraNegocioException("MOTIVO_DISPENSA_OBRIGATORIO",
                        "Indique o motivo da dispensa de requisitos.");
            }
            return;
        }
        GraduacaoRef proxima = elegibilidade.proxima();
        if (proxima == null || !proxima.codigo().equals(alvo.codigo())) {
            throw new RegraNegocioException("GRADUACAO_NAO_SEGUINTE",
                    "Só é possível fazer exame à graduação seguinte"
                            + (proxima == null ? "." : " (" + proxima.nome() + ").")
                            + " Para saltar graduações use a dispensa de requisitos.");
        }
        if (!elegibilidade.elegivel()) {
            throw new RegraNegocioException("REQUISITOS_NAO_CUMPRIDOS",
                    "Requisitos para " + alvo.nome() + " não cumpridos: " + String.join(" ", elegibilidade.pendentes()));
        }
    }
}
