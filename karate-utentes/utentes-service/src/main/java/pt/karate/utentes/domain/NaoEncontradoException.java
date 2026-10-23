package pt.karate.utentes.domain;

import java.util.UUID;

public class NaoEncontradoException extends RuntimeException {

    public NaoEncontradoException(String recurso, UUID id) {
        super(recurso + " não encontrado: " + id);
    }
}
