package pt.karate.aprendizagem.domain;

/**
 * Violação de uma regra de negócio. Mapeada para HTTP 422 com o {@code codigo} no corpo.
 */
public class RegraNegocioException extends RuntimeException {

    private final String codigo;

    public RegraNegocioException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
