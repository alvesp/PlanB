package pt.karate.utentes.consulta.indices;

import jakarta.annotation.PostConstruct;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.DynamicMapping;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Cria os índices do read-side no arranque, se ainda não existirem.
 * Corre antes dos consumidores Kafka (que dependem deste bean através da projeção).
 *
 * <p>Campos de texto usam o analisador {@code pt_folding} (minúsculas + remoção de acentos),
 * para que "joao" encontre "João". Campos novos nos eventos são ignorados
 * ({@code dynamic: false}) até serem adicionados ao mapeamento.
 */
@Component
public class Indices {

    public static final String ALUNOS = "utentes-alunos";
    public static final String PESSOAS = "utentes-pessoas";
    static final String ANALISADOR = "pt_folding";

    private static final Logger log = LoggerFactory.getLogger(Indices.class);

    private final OpenSearchClient client;

    public Indices(OpenSearchClient client) {
        this.client = client;
    }

    @PostConstruct
    void criarSeNecessario() throws IOException {
        criar(ALUNOS, mapeamentoAlunos());
        criar(PESSOAS, mapeamentoPessoas());
    }

    private void criar(String indice, TypeMapping mapeamento) throws IOException {
        if (client.indices().exists(e -> e.index(indice)).value()) {
            return;
        }
        client.indices().create(c -> c
                .index(indice)
                .settings(definicoes())
                .mappings(mapeamento));
        log.info("Índice {} criado", indice);
    }

    private static IndexSettings definicoes() {
        return IndexSettings.of(s -> s
                .numberOfShards("1")
                .numberOfReplicas("0")
                .analysis(a -> a.analyzer(ANALISADOR, an -> an
                        .custom(cu -> cu.tokenizer("standard").filter("lowercase", "asciifolding")))));
    }

    private static TypeMapping mapeamentoAlunos() {
        Map<String, Property> responsavel = Map.of(
                "pessoaId", keyword(),
                "nome", texto(),
                "telefone", keyword(),
                "email", keyword(),
                "parentesco", keyword(),
                "responsavelLegal", booleano(),
                "responsavelFiscal", booleano(),
                "contactoEmergencia", booleano(),
                "autorizadoRecolha", booleano());
        Map<String, Property> fiscal = Map.of(
                "pessoaId", keyword(),
                "nomeFaturacao", texto(),
                "nif", keyword(),
                "moradaFiscal", textoSimples(),
                "proprioAluno", booleano());
        return TypeMapping.of(m -> m
                .dynamic(DynamicMapping.False)
                .properties("alunoId", keyword())
                .properties("versao", longo())
                .properties("pessoaId", keyword())
                .properties("nome", texto())
                .properties("dataNascimento", data())
                .properties("email", keyword())
                .properties("telefone", keyword())
                .properties("estado", keyword())
                .properties("numFederacao", keyword())
                .properties("graduacaoAtual", keyword())
                .properties("dataInscricao", data())
                .properties("responsaveis", Property.of(p -> p.nested(n -> n.properties(responsavel))))
                .properties("responsavelFiscal", Property.of(p -> p.object(o -> o.properties(fiscal)))));
    }

    private static TypeMapping mapeamentoPessoas() {
        Map<String, Property> aluno = Map.of(
                "alunoId", keyword(),
                "nome", texto(),
                "parentesco", keyword(),
                "responsavelFiscal", booleano());
        return TypeMapping.of(m -> m
                .dynamic(DynamicMapping.False)
                .properties("pessoaId", keyword())
                .properties("versao", longo())
                .properties("nome", texto())
                .properties("dataNascimento", data())
                .properties("email", keyword())
                .properties("telefone", keyword())
                .properties("morada", textoSimples())
                .properties("nif", keyword())
                .properties("nomeFaturacao", texto())
                .properties("alunos", Property.of(p -> p.nested(n -> n.properties(aluno)))));
    }

    /** Texto pesquisável sem acentos, com subcampo {@code .keyword} para ordenação. */
    private static Property texto() {
        return Property.of(p -> p.text(t -> t
                .analyzer(ANALISADOR)
                .fields("keyword", f -> f.keyword(k -> k.ignoreAbove(256)))));
    }

    private static Property textoSimples() {
        return Property.of(p -> p.text(t -> t.analyzer(ANALISADOR)));
    }

    private static Property keyword() {
        return Property.of(p -> p.keyword(k -> k));
    }

    private static Property booleano() {
        return Property.of(p -> p.boolean_(b -> b));
    }

    private static Property longo() {
        return Property.of(p -> p.long_(l -> l));
    }

    private static Property data() {
        return Property.of(p -> p.date(d -> d.format("strict_date")));
    }
}
