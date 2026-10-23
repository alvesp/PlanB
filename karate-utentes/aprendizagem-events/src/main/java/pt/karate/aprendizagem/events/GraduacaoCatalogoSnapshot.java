package pt.karate.aprendizagem.events;

/** Estado de uma graduação do catálogo. */
public record GraduacaoCatalogoSnapshot(
        String codigo,
        long versao,
        String nome,
        String corFaixa,
        String tipo,
        int ordem,
        int presencasMinimas,
        int mesesMinimos,
        boolean ativa
) {
}
