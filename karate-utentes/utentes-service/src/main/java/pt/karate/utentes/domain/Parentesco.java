package pt.karate.utentes.domain;

public enum Parentesco {
    PAI,
    MAE,
    TUTOR,
    AVO,
    IRMAO,
    OUTRO,
    /** O próprio aluno (só permitido a maiores de idade). */
    PROPRIO;

    /** Só progenitores e tutores podem ser responsáveis legais de um menor. */
    public boolean podeSerResponsavelLegal() {
        return this == PAI || this == MAE || this == TUTOR || this == PROPRIO;
    }
}
