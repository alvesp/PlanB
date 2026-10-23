package pt.karate.utentes.events;

/**
 * Tópicos Kafka e cabeçalhos usados entre o write-side e o read-side.
 * <p>
 * A chave de cada mensagem é o id do agregado, para que todos os eventos do mesmo
 * aluno/pessoa caiam na mesma partição.
 */
public final class Topicos {

    public static final String ALUNOS = "karate.utentes.alunos";
    public static final String PESSOAS = "karate.utentes.pessoas";

    private Topicos() {
    }
}
