package pt.karate.aprendizagem.domain;

public enum EstadoPresenca {
    PRESENTE,
    ATRASADO,
    FALTA,
    FALTA_JUSTIFICADA;

    /** Conta para os mínimos de presenças das graduações. */
    public boolean contaComoPresenca() {
        return this == PRESENTE || this == ATRASADO;
    }
}
