package pt.karate.utentes.consulta.api;

import org.junit.jupiter.api.Test;
import pt.karate.aprendizagem.events.ProgressoAlunoSnapshot;
import pt.karate.aprendizagem.events.ProgressoAlunoSnapshot.GraduacaoResumo;
import pt.karate.aprendizagem.events.ProgressoAlunoSnapshot.Requisitos;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** A elegibilidade depende da data de hoje, por isso é calculada no read-side. */
class ElegibilidadeTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneId.of("Europe/Lisbon"));
    private final LocalDate hoje = LocalDate.now(clock);
    private final ConsultaAprendizagemService service = new ConsultaAprendizagemService(null, clock);

    private static final GraduacaoResumo KYU9 = new GraduacaoResumo("9KYU", "9º Kyu", "Amarela", 2);

    @Test
    void elegivelQuandoCumprePresencasETempo() {
        var e = service.elegibilidade(progresso(30, new Requisitos(24, 3, hoje.minusMonths(4), hoje.minusMonths(1), 5, 2)));

        assertThat(e.elegivel()).isTrue();
        assertThat(e.mesesDecorridos()).isEqualTo(4L);
        assertThat(e.pendentes()).isEmpty();
    }

    @Test
    void oTempoPassaSemNovosEventos() {
        // Mesmo snapshot: ontem ainda não cumpria o tempo, hoje já cumpre.
        var e = service.elegibilidade(progresso(30, new Requisitos(24, 3, hoje.minusMonths(3), hoje, 5, 2)));

        assertThat(e.elegivel()).isTrue();
    }

    @Test
    void indicaPendentesDePresencasETempo() {
        var e = service.elegibilidade(progresso(10, new Requisitos(24, 3, hoje.minusMonths(1), hoje.plusMonths(2), 5, 0)));

        assertThat(e.elegivel()).isFalse();
        assertThat(e.pendentes()).hasSize(2);
    }

    @Test
    void semGraduacaoSeguinteNaoEElegivel() {
        var p = new ProgressoAlunoSnapshot(UUID.randomUUID(), 1, "Rita", hoje.minusYears(10), null, null, null,
                500, 100, hoje, null, List.of(), List.of(), List.of(), List.of(), List.of());

        assertThat(service.elegibilidade(p).elegivel()).isFalse();
    }

    private ProgressoAlunoSnapshot progresso(long presencasDesde, Requisitos requisitos) {
        return new ProgressoAlunoSnapshot(UUID.randomUUID(), 1, "Rita", hoje.minusYears(1), null, null, KYU9,
                presencasDesde, presencasDesde, hoje, requisitos, List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
