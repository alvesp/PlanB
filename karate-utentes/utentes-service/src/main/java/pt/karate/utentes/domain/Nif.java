package pt.karate.utentes.domain;

/**
 * Validação de NIF português (9 dígitos, prefixo válido e dígito de controlo módulo 11).
 */
public final class Nif {

    private Nif() {
    }

    public static boolean valido(String nif) {
        if (nif == null || !nif.matches("\\d{9}")) {
            return false;
        }
        if (!prefixoValido(nif)) {
            return false;
        }
        int soma = 0;
        for (int i = 0; i < 8; i++) {
            soma += Character.digit(nif.charAt(i), 10) * (9 - i);
        }
        int resto = soma % 11;
        int controlo = resto < 2 ? 0 : 11 - resto;
        return controlo == Character.digit(nif.charAt(8), 10);
    }

    /**
     * Normaliza (remove espaços e o prefixo "PT") e valida. Lança {@link RegraNegocioException}
     * se o NIF for inválido.
     */
    public static String normalizar(String nif) {
        if (nif == null) {
            return null;
        }
        String limpo = nif.replaceAll("\\s", "").toUpperCase();
        if (limpo.startsWith("PT")) {
            limpo = limpo.substring(2);
        }
        if (!valido(limpo)) {
            throw new RegraNegocioException("NIF_INVALIDO", "O NIF indicado não é válido.");
        }
        return limpo;
    }

    private static boolean prefixoValido(String nif) {
        char primeiro = nif.charAt(0);
        // 1-3: pessoas singulares; 5: coletivas; 6: administração pública; 8: empresários
        // em nome individual (antigo); 9: irregulares/provisórios; 7: herança indivisa e outros.
        if ("12356789".indexOf(primeiro) >= 0) {
            return true;
        }
        // 45: pessoas singulares não residentes
        return nif.startsWith("45");
    }
}
