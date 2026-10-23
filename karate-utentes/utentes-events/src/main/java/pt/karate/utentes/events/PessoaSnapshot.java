package pt.karate.utentes.events;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Estado completo de uma pessoa (normalmente um responsável) após uma alteração.
 * {@code alunos} lista os alunos pelos quais a pessoa é responsável neste momento.
 */
public record PessoaSnapshot(
        UUID pessoaId,
        long versao,
        String nome,
        LocalDate dataNascimento,
        String email,
        String telefone,
        String morada,
        String nif,
        String nomeFaturacao,
        List<AlunoAssociado> alunos
) {

    public record AlunoAssociado(
            UUID alunoId,
            String nome,
            String parentesco,
            boolean responsavelFiscal
    ) {
    }
}
