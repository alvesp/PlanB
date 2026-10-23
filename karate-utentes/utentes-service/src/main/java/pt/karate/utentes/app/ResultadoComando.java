package pt.karate.utentes.app;

import java.util.UUID;

/**
 * Resposta de um comando (CQRS): o write-side não devolve dados de leitura, só a identidade
 * do agregado e a versão do evento publicado. O cliente lê o estado no read-side e pode
 * comparar {@code versao} com a do documento para saber se a projeção já está atualizada.
 */
public record ResultadoComando(UUID id, long versao) {
}
