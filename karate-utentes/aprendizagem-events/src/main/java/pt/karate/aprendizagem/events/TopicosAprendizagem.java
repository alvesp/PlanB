package pt.karate.aprendizagem.events;

/**
 * Tópicos publicados pelo aprendizagem-service.
 * Os tópicos de estado (snapshots) são compactados: guardam o último estado de cada chave.
 */
public final class TopicosAprendizagem {

    /** {@link GraduacaoAtribuida}: um evento por exame aprovado (factos, não compactado). Chave: aluno. */
    public static final String GRADUACOES = "karate.aprendizagem.graduacoes";

    /** {@link ProgressoAlunoSnapshot}. Chave: aluno. */
    public static final String PROGRESSO = "karate.aprendizagem.progresso";

    /** {@link GraduacaoCatalogoSnapshot}. Chave: {@link #chaveGraduacao(String)}. */
    public static final String CATALOGO_GRADUACOES = "karate.aprendizagem.catalogo.graduacoes";

    /** {@link TemaSnapshot}. Chave: tema. */
    public static final String TEMAS = "karate.aprendizagem.temas";

    /** {@link TurmaSnapshot}. Chave: turma. */
    public static final String TURMAS = "karate.aprendizagem.turmas";

    /** {@link AulaSnapshot} (inclui presenças). Chave: aula. */
    public static final String AULAS = "karate.aprendizagem.aulas";

    private TopicosAprendizagem() {
    }

    /**
     * As graduações são identificadas por código; a chave Kafka é um UUID derivado do código
     * (MD5 de {@code "graduacao:" + codigo}), igual ao que a migração SQL calcula com
     * {@code md5('graduacao:' || codigo)::uuid}.
     */
    public static java.util.UUID chaveGraduacao(String codigo) {
        try {
            byte[] md5 = java.security.MessageDigest.getInstance("MD5")
                    .digest(("graduacao:" + codigo).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String hex = java.util.HexFormat.of().formatHex(md5);
            return java.util.UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                    + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
