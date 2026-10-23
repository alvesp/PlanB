package pt.karate.aprendizagem.events;

import java.util.UUID;

/** Estado de um tema; {@code graduacaoCodigo} = programa de exame a que pertence (pode ser nulo). */
public record TemaSnapshot(
        UUID temaId,
        long versao,
        String nome,
        String categoria,
        String descricao,
        String graduacaoCodigo,
        boolean ativo
) {
}
