package pt.karate.aprendizagem.app;

/**
 * Resposta de um comando (CQRS): só a identidade do agregado e a versão do snapshot publicado.
 * O estado lê-se no read-side, onde o documento tem o mesmo campo {@code versao}.
 *
 * @param id     id do agregado criado/alterado (UUID ou código da graduação)
 * @param versao versão do snapshot a esperar no read-side (do aluno, para avaliações e exames)
 */
public record ResultadoComando(String id, long versao) {
}
