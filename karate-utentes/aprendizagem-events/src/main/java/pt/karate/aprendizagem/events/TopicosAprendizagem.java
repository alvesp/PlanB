package pt.karate.aprendizagem.events;

/**
 * Tópicos publicados pelo aprendizagem-service. A chave das mensagens é sempre o id do aluno.
 */
public final class TopicosAprendizagem {

    /** {@link GraduacaoAtribuida}: um evento por exame aprovado (não compactado). */
    public static final String GRADUACOES = "karate.aprendizagem.graduacoes";

    /** {@link ProgressoAlunoSnapshot}: último estado do progresso de cada aluno (compactado). */
    public static final String PROGRESSO = "karate.aprendizagem.progresso";

    private TopicosAprendizagem() {
    }
}
