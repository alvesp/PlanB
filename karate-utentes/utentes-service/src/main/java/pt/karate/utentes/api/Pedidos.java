package pt.karate.utentes.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import pt.karate.utentes.app.AlunoService;
import pt.karate.utentes.app.PessoaService;
import pt.karate.utentes.domain.DadosFiscais;
import pt.karate.utentes.domain.EstadoAluno;
import pt.karate.utentes.domain.Papeis;
import pt.karate.utentes.domain.Parentesco;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Corpos dos pedidos HTTP do write-side. */
public final class Pedidos {

    private Pedidos() {
    }

    public record PessoaRequest(
            @NotBlank @Size(max = 200) String nome,
            @NotNull @Past LocalDate dataNascimento,
            @Email @Size(max = 254) String email,
            @Size(max = 30) String telefone,
            @Size(max = 500) String morada,
            @Valid DadosFiscaisRequest dadosFiscais
    ) {
        PessoaService.DadosPessoa dados() {
            return new PessoaService.DadosPessoa(nome, dataNascimento, email, telefone, morada);
        }

        DadosFiscais fiscais() {
            return dadosFiscais == null ? null : dadosFiscais.paraDominio();
        }
    }

    public record DadosPessoaRequest(
            @NotBlank @Size(max = 200) String nome,
            @NotNull @Past LocalDate dataNascimento,
            @Email @Size(max = 254) String email,
            @Size(max = 30) String telefone,
            @Size(max = 500) String morada
    ) {
        PessoaService.DadosPessoa dados() {
            return new PessoaService.DadosPessoa(nome, dataNascimento, email, telefone, morada);
        }
    }

    public record DadosFiscaisRequest(
            @NotBlank String nif,
            @NotBlank @Size(max = 200) String nomeFaturacao,
            @NotBlank @Size(max = 500) String moradaFiscal
    ) {
        DadosFiscais paraDominio() {
            return new DadosFiscais(nif, nomeFaturacao, moradaFiscal);
        }
    }

    public record PapeisRequest(
            @NotNull Parentesco parentesco,
            boolean responsavelLegal,
            boolean responsavelFiscal,
            boolean contactoEmergencia,
            boolean autorizadoRecolha
    ) {
        Papeis paraDominio() {
            return new Papeis(parentesco, responsavelLegal, responsavelFiscal, contactoEmergencia, autorizadoRecolha);
        }
    }

    public record ResponsavelRequest(
            @NotNull UUID pessoaId,
            @NotNull @Valid PapeisRequest papeis
    ) {
        AlunoService.NovoResponsavel paraComando() {
            return new AlunoService.NovoResponsavel(pessoaId, papeis.paraDominio());
        }
    }

    public record InscricaoRequest(
            @NotNull UUID pessoaId,
            @Size(max = 30) String numFederacao,
            @NotEmpty List<@Valid @NotNull ResponsavelRequest> responsaveis
    ) {
        AlunoService.NovaInscricao paraComando() {
            return new AlunoService.NovaInscricao(pessoaId, numFederacao,
                    responsaveis.stream().map(ResponsavelRequest::paraComando).toList());
        }
    }

    public record DadosAlunoRequest(
            @Size(max = 30) String numFederacao,
            @NotNull EstadoAluno estado
    ) {
    }

    public record ResponsavelFiscalRequest(@NotNull UUID pessoaId) {
    }
}
