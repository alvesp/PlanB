package pt.karate.utentes.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlunoTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneId.of("Europe/Lisbon"));
    private final LocalDate hoje = LocalDate.now(clock);

    private final Pessoa crianca = pessoa("Rita", hoje.minusYears(9), null);
    private final Pessoa mae = pessoa("Ana", hoje.minusYears(38), new DadosFiscais("123456789", "Ana Silva", "Rua A, Porto"));
    private final Pessoa pai = pessoa("Rui", hoje.minusYears(40), new DadosFiscais("234567899", "Rui Silva", "Rua B, Porto"));

    private static final Papeis MAE_LEGAL_FISCAL = new Papeis(Parentesco.MAE, true, true, true, true);
    private static final Papeis PAI_LEGAL = new Papeis(Parentesco.PAI, true, false, true, true);

    @Test
    void inscricaoDeMenorExigeResponsavelFiscal() {
        Map<Pessoa, Papeis> soPaiSemFiscal = Map.of(pai, PAI_LEGAL);
        assertThatThrownBy(() -> Aluno.inscrever(UUID.randomUUID(), crianca, null, "10 kyu", soPaiSemFiscal, clock))
                .isInstanceOf(RegraNegocioException.class)
                .extracting("codigo").isEqualTo("RESPONSAVEL_FISCAL_UNICO");
    }

    @Test
    void transferirResponsavelFiscalMantemHistorico() {
        Aluno aluno = inscreverComMaeEPai();

        aluno.definirResponsavelFiscal(pai.getId(), clock);

        assertThat(aluno.responsavelFiscal()).get()
                .extracting(r -> r.getResponsavel().getId()).isEqualTo(pai.getId());
        assertThat(aluno.relacoesVigentes()).hasSize(2);
        // 2 iniciais + nova linha da mãe (sem fiscal) + nova linha do pai (com fiscal)
        assertThat(aluno.getRelacoes()).hasSize(4);
        assertThat(aluno.getRelacoes()).filteredOn(r -> !r.vigente()).hasSize(2);
    }

    @Test
    void naoPermiteTerminarRelacaoComResponsavelFiscal() {
        Aluno aluno = inscreverComMaeEPai();

        assertThatThrownBy(() -> aluno.terminarRelacao(mae.getId(), clock))
                .isInstanceOf(RegraNegocioException.class)
                .extracting("codigo").isEqualTo("RESPONSAVEL_FISCAL_UNICO");
    }

    @Test
    void adicionarNovoResponsavelFiscalRetiraOPapelAoAnterior() {
        Aluno aluno = inscreverComMaeEPai();
        Pessoa avo = pessoa("Maria", hoje.minusYears(68), new DadosFiscais("999999990", "Maria Costa", "Rua C"));

        aluno.adicionarResponsavel(avo, new Papeis(Parentesco.AVO, false, true, true, false), clock);

        assertThat(aluno.responsavelFiscal()).get()
                .extracting(r -> r.getResponsavel().getId()).isEqualTo(avo.getId());
        assertThat(aluno.relacoesVigentes())
                .filteredOn(RelacaoResponsavel::isResponsavelFiscal).hasSize(1);
    }

    @Test
    void graduacaoMaisRecenteAtualizaGraduacaoAtual() {
        Aluno aluno = inscreverComMaeEPai();

        aluno.registarGraduacao("9 kyu", hoje.minusMonths(1), "Sensei Kato", clock);
        aluno.registarGraduacao("10 kyu", hoje.minusYears(1), "Sensei Kato", clock);

        assertThat(aluno.getGraduacaoAtual()).isEqualTo("9 kyu");
        assertThat(aluno.getGraduacoes()).hasSize(2);
    }

    private Aluno inscreverComMaeEPai() {
        Map<Pessoa, Papeis> responsaveis = new LinkedHashMap<>();
        responsaveis.put(mae, MAE_LEGAL_FISCAL);
        responsaveis.put(pai, PAI_LEGAL);
        return Aluno.inscrever(UUID.randomUUID(), crianca, "FNK-123", "10 kyu", responsaveis, clock);
    }

    private Pessoa pessoa(String nome, LocalDate nascimento, DadosFiscais dadosFiscais) {
        return new Pessoa(UUID.randomUUID(), nome, nascimento, null, null, null, dadosFiscais, clock);
    }
}
