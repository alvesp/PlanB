package pt.karate.aprendizagem.domain;

import org.junit.jupiter.api.Test;
import pt.karate.aprendizagem.domain.RequisitosGraduacao.Elegibilidade;
import pt.karate.aprendizagem.domain.RequisitosGraduacao.GraduacaoRef;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequisitosGraduacaoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 23);

    private static final GraduacaoRef KYU10 = new GraduacaoRef("10KYU", "10º Kyu", 1, 0, 0);
    private static final GraduacaoRef KYU9 = new GraduacaoRef("9KYU", "9º Kyu", 2, 24, 3);
    private static final GraduacaoRef KYU8 = new GraduacaoRef("8KYU", "8º Kyu", 3, 24, 3);

    @Test
    void elegivelComPresencasETempoSuficientes() {
        Elegibilidade e = RequisitosGraduacao.avaliar(KYU9, HOJE.minusMonths(4), 30, 5, 3, HOJE);

        assertThat(e.elegivel()).isTrue();
        assertThat(e.mesesDecorridos()).isEqualTo(4L);
    }

    @Test
    void naoElegivelIndicaTodasAsPendencias() {
        Elegibilidade e = RequisitosGraduacao.avaliar(KYU9, HOJE.minusMonths(2), 10, 5, 0, HOJE);

        assertThat(e.elegivel()).isFalse();
        assertThat(e.pendentes()).hasSize(2);
    }

    @Test
    void primeiraGraduacaoSemMinimosEElegivelDeImediato() {
        Elegibilidade e = RequisitosGraduacao.avaliar(KYU10, HOJE, 0, 0, 0, HOJE);

        assertThat(e.elegivel()).isTrue();
    }

    @Test
    void semGraduacaoSeguinteNaoEElegivel() {
        Elegibilidade e = RequisitosGraduacao.avaliar(null, HOJE.minusYears(5), 500, 0, 0, HOJE);

        assertThat(e.elegivel()).isFalse();
    }

    @Test
    void temasPorDominarNaoBloqueiamElegibilidade() {
        Elegibilidade e = RequisitosGraduacao.avaliar(KYU9, HOJE.minusMonths(6), 40, 8, 0, HOJE);

        assertThat(e.elegivel()).isTrue();
    }

    @Test
    void exameAGraduacaoSeguinteComRequisitosEValido() {
        Elegibilidade e = RequisitosGraduacao.avaliar(KYU9, HOJE.minusMonths(4), 30, 0, 0, HOJE);

        assertThatCode(() -> RequisitosGraduacao.validarExame(KYU10, HOJE.minusMonths(4), KYU9, HOJE, HOJE, e,
                false, null)).doesNotThrowAnyException();
    }

    @Test
    void exameSemRequisitosFalha() {
        Elegibilidade e = RequisitosGraduacao.avaliar(KYU9, HOJE.minusMonths(1), 5, 0, 0, HOJE);

        assertCodigo("REQUISITOS_NAO_CUMPRIDOS",
                () -> RequisitosGraduacao.validarExame(KYU10, HOJE.minusMonths(1), KYU9, HOJE, HOJE, e, false, null));
    }

    @Test
    void saltarGraduacaoSemDispensaFalha() {
        Elegibilidade e = RequisitosGraduacao.avaliar(KYU9, HOJE.minusMonths(6), 50, 0, 0, HOJE);

        assertCodigo("GRADUACAO_NAO_SEGUINTE",
                () -> RequisitosGraduacao.validarExame(KYU10, HOJE.minusMonths(6), KYU8, HOJE, HOJE, e, false, null));
    }

    @Test
    void dispensaPermiteSaltarGraduacaoMasExigeMotivo() {
        Elegibilidade e = RequisitosGraduacao.avaliar(KYU10, null, 0, 0, 0, HOJE);

        assertCodigo("MOTIVO_DISPENSA_OBRIGATORIO",
                () -> RequisitosGraduacao.validarExame(null, null, KYU8, HOJE, HOJE, e, true, " "));
        assertThatCode(() -> RequisitosGraduacao.validarExame(null, null, KYU8, HOJE, HOJE, e, true,
                "Graduação obtida noutro clube")).doesNotThrowAnyException();
    }

    @Test
    void exameAGraduacaoIgualOuInferiorFalha() {
        Elegibilidade e = RequisitosGraduacao.avaliar(KYU8, HOJE.minusMonths(6), 50, 0, 0, HOJE);

        assertCodigo("GRADUACAO_NAO_SUPERIOR",
                () -> RequisitosGraduacao.validarExame(KYU9, HOJE.minusMonths(6), KYU9, HOJE, HOJE, e, true, "x"));
    }

    @Test
    void exameNoFuturoFalha() {
        Elegibilidade e = RequisitosGraduacao.avaliar(KYU10, null, 0, 0, 0, HOJE);

        assertCodigo("DATA_EXAME_FUTURA",
                () -> RequisitosGraduacao.validarExame(null, null, KYU10, HOJE.plusDays(1), HOJE, e, false, null));
    }

    @Test
    void exameAnteriorAGraduacaoAtualFalha() {
        Elegibilidade e = RequisitosGraduacao.avaliar(KYU9, HOJE.minusMonths(6), 50, 0, 0, HOJE);

        assertCodigo("DATA_ANTERIOR_A_GRADUACAO_ATUAL",
                () -> RequisitosGraduacao.validarExame(KYU10, HOJE.minusMonths(6), KYU9, HOJE.minusMonths(7), HOJE,
                        e, false, null));
    }

    private static void assertCodigo(String codigo, Runnable acao) {
        assertThatThrownBy(acao::run)
                .isInstanceOf(RegraNegocioException.class)
                .extracting("codigo").isEqualTo(codigo);
    }
}
