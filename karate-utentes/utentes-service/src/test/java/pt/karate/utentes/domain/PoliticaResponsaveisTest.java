package pt.karate.utentes.domain;

import org.junit.jupiter.api.Test;
import pt.karate.utentes.domain.PoliticaResponsaveis.PessoaInfo;
import pt.karate.utentes.domain.PoliticaResponsaveis.RelacaoInfo;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticaResponsaveisTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 23);

    private final PessoaInfo menor = pessoa(HOJE.minusYears(10), false);
    private final PessoaInfo adulto = pessoa(HOJE.minusYears(25), true);
    private final PessoaInfo mae = pessoa(HOJE.minusYears(40), true);
    private final PessoaInfo pai = pessoa(HOJE.minusYears(42), false);

    @Test
    void menorComMaeResponsavelLegalEFiscalEValido() {
        assertThatCode(() -> validar(menor, rel(mae, Parentesco.MAE, true, true)))
                .doesNotThrowAnyException();
    }

    @Test
    void paisSeparadosUmLegalOutroFiscal() {
        assertThatCode(() -> validar(menor,
                rel(pai, Parentesco.PAI, true, false),
                rel(mae, Parentesco.MAE, true, true)))
                .doesNotThrowAnyException();
    }

    @Test
    void semResponsavelFiscalFalha() {
        assertCodigo("RESPONSAVEL_FISCAL_UNICO", () -> validar(menor, rel(pai, Parentesco.PAI, true, false)));
    }

    @Test
    void doisResponsaveisFiscaisFalha() {
        PessoaInfo pai2 = pessoa(HOJE.minusYears(42), true);
        assertCodigo("RESPONSAVEL_FISCAL_UNICO", () -> validar(menor,
                rel(pai2, Parentesco.PAI, true, true),
                rel(mae, Parentesco.MAE, true, true)));
    }

    @Test
    void responsavelFiscalSemDadosFiscaisFalha() {
        assertCodigo("RESPONSAVEL_FISCAL_SEM_DADOS", () -> validar(menor, rel(pai, Parentesco.PAI, true, true)));
    }

    @Test
    void menorSemResponsavelLegalFalha() {
        PessoaInfo avo = pessoa(HOJE.minusYears(70), true);
        assertCodigo("MENOR_SEM_RESPONSAVEL_LEGAL", () -> validar(menor, rel(avo, Parentesco.AVO, false, true)));
    }

    @Test
    void avoNaoPodeSerResponsavelLegal() {
        PessoaInfo avo = pessoa(HOJE.minusYears(70), true);
        assertCodigo("PARENTESCO_NAO_PERMITE_RESPONSABILIDADE_LEGAL",
                () -> validar(menor, rel(avo, Parentesco.AVO, true, true)));
    }

    @Test
    void avoPodeSerApenasResponsavelFiscal() {
        PessoaInfo avo = pessoa(HOJE.minusYears(70), true);
        assertThatCode(() -> validar(menor,
                rel(pai, Parentesco.PAI, true, false),
                rel(avo, Parentesco.AVO, false, true)))
                .doesNotThrowAnyException();
    }

    @Test
    void irmaoMenorNaoPodeSerResponsavelFiscal() {
        PessoaInfo irmao = pessoa(HOJE.minusYears(17), true);
        assertCodigo("RESPONSAVEL_FISCAL_MENOR", () -> validar(menor,
                rel(mae, Parentesco.MAE, true, false),
                rel(irmao, Parentesco.IRMAO, false, true)));
    }

    @Test
    void adultoPodeSerResponsavelPorSiProprio() {
        assertThatCode(() -> validar(adulto, rel(adulto, Parentesco.PROPRIO, true, true)))
                .doesNotThrowAnyException();
    }

    @Test
    void menorNaoPodeSerResponsavelPorSiProprio() {
        PessoaInfo menorComNif = pessoa(HOJE.minusYears(16), true);
        assertCodigo("MENOR_NAO_PODE_SER_RESPONSAVEL",
                () -> validar(menorComNif, rel(menorComNif, Parentesco.PROPRIO, true, true)));
    }

    @Test
    void proprioComOutraPessoaFalha() {
        assertCodigo("PARENTESCO_PROPRIO_INVALIDO", () -> validar(adulto, rel(mae, Parentesco.PROPRIO, true, true)));
    }

    @Test
    void faz18AnosHojeEMaior() {
        PessoaInfo aniversariante = pessoa(HOJE.minusYears(18), true);
        assertThatCode(() -> validar(aniversariante, rel(aniversariante, Parentesco.PROPRIO, true, true)))
                .doesNotThrowAnyException();
    }

    @Test
    void responsavelDuplicadoFalha() {
        assertCodigo("RESPONSAVEL_DUPLICADO", () -> validar(menor,
                rel(mae, Parentesco.MAE, true, true),
                rel(mae, Parentesco.MAE, true, false)));
    }

    private static PessoaInfo pessoa(LocalDate nascimento, boolean temDadosFiscais) {
        return new PessoaInfo(UUID.randomUUID(), nascimento, temDadosFiscais);
    }

    private static RelacaoInfo rel(PessoaInfo p, Parentesco parentesco, boolean legal, boolean fiscal) {
        return new RelacaoInfo(p, parentesco, legal, fiscal);
    }

    private static void validar(PessoaInfo aluno, RelacaoInfo... relacoes) {
        PoliticaResponsaveis.validar(aluno, List.of(relacoes), HOJE);
    }

    private static void assertCodigo(String codigo, Runnable acao) {
        assertThatThrownBy(acao::run)
                .isInstanceOf(RegraNegocioException.class)
                .extracting("codigo").isEqualTo(codigo);
    }
}
