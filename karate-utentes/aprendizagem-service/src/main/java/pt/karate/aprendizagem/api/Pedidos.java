package pt.karate.aprendizagem.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import pt.karate.aprendizagem.app.AlunoAprendizagemService;
import pt.karate.aprendizagem.app.AulaService;
import pt.karate.aprendizagem.app.CatalogoService;
import pt.karate.aprendizagem.app.TurmaService;
import pt.karate.aprendizagem.domain.CategoriaTema;
import pt.karate.aprendizagem.domain.EstadoPresenca;
import pt.karate.aprendizagem.domain.NivelDominio;
import pt.karate.aprendizagem.domain.ResultadoExame;
import pt.karate.aprendizagem.domain.TipoGraduacao;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Corpos dos pedidos HTTP do aprendizagem-service. */
public final class Pedidos {

    private Pedidos() {
    }

    public record GraduacaoRequest(
            @Pattern(regexp = "[A-Z0-9_]{1,20}") String codigo,
            @NotBlank @Size(max = 50) String nome,
            @NotBlank @Size(max = 50) String corFaixa,
            @NotNull TipoGraduacao tipo,
            @Min(1) int ordem,
            @Min(0) int presencasMinimas,
            @Min(0) int mesesMinimos,
            Boolean ativa
    ) {
        CatalogoService.DadosGraduacao dados() {
            return new CatalogoService.DadosGraduacao(nome, corFaixa, tipo, ordem, presencasMinimas, mesesMinimos,
                    ativa == null || ativa);
        }
    }

    public record TemaRequest(
            @NotBlank @Size(max = 200) String nome,
            @NotNull CategoriaTema categoria,
            @Size(max = 4000) String descricao,
            @Size(max = 20) String graduacao,
            Boolean ativo
    ) {
        CatalogoService.DadosTema dados() {
            return new CatalogoService.DadosTema(nome, categoria, descricao, graduacao, ativo == null || ativo);
        }
    }

    public record TurmaRequest(
            @NotBlank @Size(max = 100) String nome,
            @Size(max = 500) String descricao,
            @Size(max = 200) String instrutor,
            Boolean ativa
    ) {
        TurmaService.DadosTurma dados() {
            return new TurmaService.DadosTurma(nome, descricao, instrutor, ativa == null || ativa);
        }
    }

    public record AulaRequest(
            UUID turmaId,
            @NotNull LocalDate data,
            @NotNull LocalTime horaInicio,
            @Min(1) @Max(600) int duracaoMinutos,
            @Size(max = 200) String instrutor,
            @Size(max = 4000) String sumario,
            Set<UUID> temas
    ) {
        AulaService.DadosAula dados() {
            return new AulaService.DadosAula(turmaId, data, horaInicio, duracaoMinutos, instrutor, sumario, temas);
        }
    }

    public record PresencaRequest(
            @NotNull UUID alunoId,
            @NotNull EstadoPresenca estado,
            @Size(max = 500) String observacao
    ) {
        AulaService.RegistoPresenca registo() {
            return new AulaService.RegistoPresenca(alunoId, estado, observacao);
        }
    }

    public record PresencasRequest(@NotEmpty @Size(max = 200) List<@Valid @NotNull PresencaRequest> registos) {
    }

    public record AvaliacaoRequest(
            @NotNull UUID temaId,
            @NotNull NivelDominio nivel,
            @PastOrPresent LocalDate data,
            @Size(max = 200) String avaliador,
            @Size(max = 500) String observacao
    ) {
        AlunoAprendizagemService.NovaAvaliacao comando() {
            return new AlunoAprendizagemService.NovaAvaliacao(temaId, nivel, data, avaliador, observacao);
        }
    }

    public record ExameRequest(
            @NotBlank String graduacao,
            @PastOrPresent LocalDate data,
            @NotBlank @Size(max = 200) String examinador,
            @NotNull ResultadoExame resultado,
            @Size(max = 1000) String observacoes,
            boolean dispensarRequisitos,
            @Size(max = 500) String motivoDispensa
    ) {
        AlunoAprendizagemService.NovoExame comando() {
            return new AlunoAprendizagemService.NovoExame(graduacao, data, examinador, resultado, observacoes,
                    dispensarRequisitos, motivoDispensa);
        }
    }
}
