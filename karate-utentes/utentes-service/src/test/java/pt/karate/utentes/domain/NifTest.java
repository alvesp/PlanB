package pt.karate.utentes.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NifTest {

    @ParameterizedTest
    @ValueSource(strings = {"123456789", "234567899", "501442600", "999999990"})
    void aceitaNifsValidos(String nif) {
        assertThat(Nif.valido(nif)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456780", "12345678", "1234567890", "abcdefghi", "023456789", "412345670"})
    void rejeitaNifsInvalidos(String nif) {
        assertThat(Nif.valido(nif)).isFalse();
    }

    @Test
    void normalizaPrefixoEEspacos() {
        assertThat(Nif.normalizar(" PT 123 456 789 ")).isEqualTo("123456789");
    }

    @Test
    void normalizarLancaRegraNegocioParaNifInvalido() {
        assertThatThrownBy(() -> Nif.normalizar("123456780"))
                .isInstanceOf(RegraNegocioException.class)
                .extracting("codigo").isEqualTo("NIF_INVALIDO");
    }
}
