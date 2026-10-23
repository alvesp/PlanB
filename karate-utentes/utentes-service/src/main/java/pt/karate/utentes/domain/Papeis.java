package pt.karate.utentes.domain;

import java.util.Objects;

/**
 * Papéis de um responsável na relação com um aluno.
 */
public record Papeis(
        Parentesco parentesco,
        boolean responsavelLegal,
        boolean responsavelFiscal,
        boolean contactoEmergencia,
        boolean autorizadoRecolha
) {

    public Papeis {
        Objects.requireNonNull(parentesco, "parentesco");
    }

    public Papeis comFiscal(boolean fiscal) {
        return new Papeis(parentesco, responsavelLegal, fiscal, contactoEmergencia, autorizadoRecolha);
    }
}
