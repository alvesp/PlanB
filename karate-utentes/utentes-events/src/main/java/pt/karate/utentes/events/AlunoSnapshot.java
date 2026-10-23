package pt.karate.utentes.events;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Estado completo de um aluno após uma alteração (evento "state-carrying").
 * <p>
 * O read-side não precisa de reconstruir estado a partir de deltas: indexa este snapshot
 * com versionamento externo ({@code versao}), o que torna a projeção idempotente e
 * imune a eventos duplicados ou fora de ordem.
 */
public record AlunoSnapshot(
        UUID alunoId,
        long versao,
        UUID pessoaId,
        String nome,
        LocalDate dataNascimento,
        String email,
        String telefone,
        String estado,
        String numFederacao,
        String graduacaoAtual,
        LocalDate dataInscricao,
        List<ResponsavelResumo> responsaveis,
        ResponsavelFiscalResumo responsavelFiscal
) {

    public record ResponsavelResumo(
            UUID pessoaId,
            String nome,
            String telefone,
            String email,
            String parentesco,
            boolean responsavelLegal,
            boolean responsavelFiscal,
            boolean contactoEmergencia,
            boolean autorizadoRecolha
    ) {
    }

    public record ResponsavelFiscalResumo(
            UUID pessoaId,
            String nomeFaturacao,
            String nif,
            String moradaFiscal,
            boolean proprioAluno
    ) {
    }
}
