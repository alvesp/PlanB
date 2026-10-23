package pt.karate.aprendizagem.domain;

public class NaoEncontradoException extends RuntimeException {

    public NaoEncontradoException(String recurso, Object id) {
        super(recurso + " não encontrado: " + id);
    }
}
