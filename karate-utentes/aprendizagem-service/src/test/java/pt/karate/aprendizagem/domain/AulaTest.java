package pt.karate.aprendizagem.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AulaTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T18:00:00Z"), ZoneId.of("Europe/Lisbon"));
    private final LocalDate hoje = LocalDate.now(clock);

    private final AlunoRef rita = new AlunoRef(UUID.randomUUID(), "Rita", hoje.minusYears(9), hoje.minusYears(1), "ATIVO");
    private final AlunoRef tiago = new AlunoRef(UUID.randomUUID(), "Tiago", hoje.minusYears(10), hoje.minusYears(1), "INATIVO");
    private final Tema heianShodan = new Tema(UUID.randomUUID(), "Heian Shodan", CategoriaTema.KATA, null, null);

    @Test
    void registarECorrigirPresenca() {
        Aula aula = aula(hoje);

        boolean primeiro = aula.registarPresenca(rita, EstadoPresenca.FALTA, null, clock);
        boolean correcao = aula.registarPresenca(rita, EstadoPresenca.PRESENTE, "chegou a horas", clock);
        boolean igual = aula.registarPresenca(rita, EstadoPresenca.PRESENTE, "chegou a horas", clock);

        assertThat(primeiro).isTrue();
        assertThat(correcao).isTrue();
        assertThat(igual).isFalse();
        assertThat(aula.getPresencas()).hasSize(1);
        assertThat(aula.presenca(rita.getId())).get()
                .extracting(p -> p.getEstado()).isEqualTo(EstadoPresenca.PRESENTE);
    }

    @Test
    void naoRegistaPresencasEmAulasFuturas() {
        Aula aula = aula(hoje.plusDays(1));

        assertCodigo("AULA_FUTURA", () -> aula.registarPresenca(rita, EstadoPresenca.PRESENTE, null, clock));
    }

    @Test
    void naoRegistaPresencasDeAlunosInativos() {
        Aula aula = aula(hoje);

        assertCodigo("ALUNO_INATIVO", () -> aula.registarPresenca(tiago, EstadoPresenca.PRESENTE, null, clock));
    }

    @Test
    void naoCancelaAulaComPresencas() {
        Aula aula = aula(hoje);
        aula.registarPresenca(rita, EstadoPresenca.PRESENTE, null, clock);

        assertCodigo("AULA_COM_PRESENCAS", aula::cancelar);
    }

    @Test
    void aulaCanceladaNaoAceitaPresencas() {
        Aula aula = aula(hoje);
        aula.cancelar();

        assertCodigo("AULA_CANCELADA", () -> aula.registarPresenca(rita, EstadoPresenca.PRESENTE, null, clock));
    }

    @Test
    void naoMudaDataDeAulaComPresencas() {
        Aula aula = aula(hoje);
        aula.registarPresenca(rita, EstadoPresenca.PRESENTE, null, clock);

        assertCodigo("AULA_COM_PRESENCAS",
                () -> aula.atualizar(hoje.minusDays(1), LocalTime.of(18, 0), 60, null, null));
    }

    @Test
    void faltaNaoContaComoPresenca() {
        assertThat(EstadoPresenca.FALTA.contaComoPresenca()).isFalse();
        assertThat(EstadoPresenca.FALTA_JUSTIFICADA.contaComoPresenca()).isFalse();
        assertThat(EstadoPresenca.ATRASADO.contaComoPresenca()).isTrue();
    }

    private Aula aula(LocalDate data) {
        return new Aula(UUID.randomUUID(), null, data, LocalTime.of(18, 0), 60, "Sensei Kato", "Kata",
                List.of(heianShodan));
    }

    private static void assertCodigo(String codigo, Runnable acao) {
        assertThatThrownBy(acao::run)
                .isInstanceOf(RegraNegocioException.class)
                .extracting("codigo").isEqualTo(codigo);
    }
}
