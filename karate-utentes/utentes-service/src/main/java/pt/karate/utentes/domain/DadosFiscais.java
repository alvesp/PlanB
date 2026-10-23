package pt.karate.utentes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Dados usados para faturar. O NIF é validado e normalizado na construção.
 */
@Embeddable
public record DadosFiscais(
        @Column(name = "nif", length = 9) String nif,
        @Column(name = "nome_faturacao") String nomeFaturacao,
        @Column(name = "morada_fiscal") String moradaFiscal
) {

    public DadosFiscais {
        nif = Nif.normalizar(nif);
        if (nif == null || isBlank(nomeFaturacao) || isBlank(moradaFiscal)) {
            throw new RegraNegocioException("DADOS_FISCAIS_INCOMPLETOS",
                    "NIF, nome de faturação e morada fiscal são obrigatórios.");
        }
        nomeFaturacao = nomeFaturacao.strip();
        moradaFiscal = moradaFiscal.strip();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
