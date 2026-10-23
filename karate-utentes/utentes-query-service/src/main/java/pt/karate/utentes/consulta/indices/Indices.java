package pt.karate.utentes.consulta.indices;

import jakarta.annotation.PostConstruct;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.mapping.DynamicMapping;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Modelos de leitura (CQRS): um índice por tipo de snapshot publicado pelos serviços de escrita.
 *
 * <p>No arranque, cria os índices que não existem e acrescenta aos existentes os campos novos
 * do mapeamento (alterações aditivas). Corre antes dos consumidores Kafka, que dependem deste
 * bean através da projeção.
 *
 * <p>Os campos de texto usam o analisador {@code pt_folding} (minúsculas e sem acentos), para que
 * "joao" encontre "João". Campos dos eventos que não estão no mapeamento ficam guardados no
 * documento mas não são pesquisáveis ({@code dynamic: false}).
 */
@Component
public class Indices {

    public static final String ALUNOS = "utentes-alunos";
    public static final String PESSOAS = "utentes-pessoas";
    public static final String PROGRESSO = "aprendizagem-progresso";
    public static final String GRADUACOES = "aprendizagem-graduacoes";
    public static final String TEMAS = "aprendizagem-temas";
    public static final String TURMAS = "aprendizagem-turmas";
    public static final String AULAS = "aprendizagem-aulas";
    static final String ANALISADOR = "pt_folding";

    private static final Logger log = LoggerFactory.getLogger(Indices.class);

    private final OpenSearchClient client;

    public Indices(OpenSearchClient client) {
        this.client = client;
    }

    @PostConstruct
    void criarOuAtualizar() throws IOException {
        garantir(ALUNOS, alunos());
        garantir(PESSOAS, pessoas());
        garantir(PROGRESSO, progresso());
        garantir(GRADUACOES, graduacoes());
        garantir(TEMAS, temas());
        garantir(TURMAS, turmas());
        garantir(AULAS, aulas());
    }

    private void garantir(String indice, Map<String, Property> campos) throws IOException {
        if (!client.indices().exists(e -> e.index(indice)).value()) {
            client.indices().create(c -> c
                    .index(indice)
                    .settings(definicoes())
                    .mappings(m -> m.dynamic(DynamicMapping.False).properties(campos)));
            log.info("Índice {} criado", indice);
            return;
        }
        try {
            client.indices().putMapping(p -> p.index(indice).properties(campos));
        } catch (OpenSearchException e) {
            // Alteração incompatível (ex.: mudar o tipo de um campo): exige reindexar.
            log.error("Não foi possível atualizar o mapeamento de {}: {}. Apague o índice e reprocesse os tópicos.",
                    indice, e.getMessage());
            throw e;
        }
    }

    private static IndexSettings definicoes() {
        return IndexSettings.of(s -> s
                .numberOfShards("1")
                .numberOfReplicas("0")
                .analysis(a -> a.analyzer(ANALISADOR, an -> an
                        .custom(cu -> cu.tokenizer("standard").filter("lowercase", "asciifolding")))));
    }

    // ---- Utentes ----

    private static Map<String, Property> alunos() {
        Map<String, Property> responsavel = campos(
                "pessoaId", keyword(), "nome", texto(), "telefone", keyword(), "email", keyword(),
                "parentesco", keyword(), "responsavelLegal", booleano(), "responsavelFiscal", booleano(),
                "contactoEmergencia", booleano(), "autorizadoRecolha", booleano(),
                "validoDesde", data(), "validoAte", data());
        Map<String, Property> fiscal = campos(
                "pessoaId", keyword(), "nome", texto(), "parentesco", keyword(), "nomeFaturacao", texto(),
                "nif", keyword(), "moradaFiscal", textoSimples(), "proprioAluno", booleano(), "desde", data());
        Map<String, Property> graduacao = campos(
                "codigo", keyword(), "nome", keyword(), "ordem", inteiro(), "data", data(), "examinador", textoSimples());
        return campos(
                "alunoId", keyword(), "versao", longo(), "pessoaId", keyword(), "nome", texto(),
                "dataNascimento", data(), "email", keyword(), "telefone", keyword(), "estado", keyword(),
                "numFederacao", keyword(), "graduacaoAtual", keyword(), "dataInscricao", data(),
                "responsaveis", nested(responsavel),
                "responsavelFiscal", objeto(fiscal),
                "historicoResponsaveis", nested(responsavel),
                "graduacoes", nested(graduacao));
    }

    private static Map<String, Property> pessoas() {
        Map<String, Property> aluno = campos(
                "alunoId", keyword(), "nome", texto(), "parentesco", keyword(), "responsavelFiscal", booleano());
        return campos(
                "pessoaId", keyword(), "versao", longo(), "nome", texto(), "dataNascimento", data(),
                "email", keyword(), "telefone", keyword(), "morada", textoSimples(), "nif", keyword(),
                "nomeFaturacao", texto(), "alunos", nested(aluno));
    }

    // ---- Aprendizagem ----

    private static Map<String, Property> progresso() {
        Map<String, Property> graduacao = campos(
                "codigo", keyword(), "nome", keyword(), "corFaixa", keyword(), "ordem", inteiro());
        Map<String, Property> obtida = campos(
                "exameId", keyword(), "codigo", keyword(), "nome", keyword(), "data", data(),
                "examinador", textoSimples());
        Map<String, Property> exame = campos(
                "exameId", keyword(), "codigo", keyword(), "nome", keyword(), "data", data(),
                "examinador", textoSimples(), "resultado", keyword(), "requisitosDispensados", booleano());
        Map<String, Property> tema = campos(
                "temaId", keyword(), "nome", texto(), "categoria", keyword(), "graduacaoCodigo", keyword(),
                "nivel", keyword(), "data", data());
        Map<String, Property> requisitos = campos(
                "presencasMinimas", inteiro(), "mesesMinimos", inteiro(), "referencia", data(),
                "cumpreTempoEm", data(), "temasPrograma", inteiro(), "temasDominados", inteiro());
        return campos(
                "alunoId", keyword(), "versao", longo(), "nome", texto(), "dataInscricao", data(),
                "graduacaoAtual", objeto(graduacao), "dataGraduacaoAtual", data(),
                "proximaGraduacao", objeto(graduacao),
                "totalPresencas", longo(), "presencasDesdeUltimaGraduacao", longo(), "ultimaPresenca", data(),
                "requisitos", objeto(requisitos),
                "historico", nested(obtida), "exames", nested(exame), "temas", nested(tema));
    }

    private static Map<String, Property> graduacoes() {
        return campos(
                "codigo", keyword(), "versao", longo(), "nome", keyword(), "corFaixa", keyword(),
                "tipo", keyword(), "ordem", inteiro(), "presencasMinimas", inteiro(), "mesesMinimos", inteiro(),
                "ativa", booleano());
    }

    private static Map<String, Property> temas() {
        return campos(
                "temaId", keyword(), "versao", longo(), "nome", texto(), "categoria", keyword(),
                "descricao", textoSimples(), "graduacaoCodigo", keyword(), "ativo", booleano());
    }

    private static Map<String, Property> turmas() {
        return campos(
                "turmaId", keyword(), "versao", longo(), "nome", texto(), "descricao", textoSimples(),
                "instrutor", texto(), "ativa", booleano(), "alunos", keyword());
    }

    private static Map<String, Property> aulas() {
        Map<String, Property> tema = campos("temaId", keyword(), "nome", texto(), "categoria", keyword());
        Map<String, Property> presenca = campos("alunoId", keyword(), "estado", keyword());
        return campos(
                "aulaId", keyword(), "versao", longo(), "turmaId", keyword(), "turma", texto(), "data", data(),
                "horaInicio", keyword(), "duracaoMinutos", inteiro(), "instrutor", texto(),
                "sumario", textoSimples(), "cancelada", booleano(),
                "temas", nested(tema), "presencas", nested(presenca));
    }

    // ---- Tipos de campo ----

    private static Map<String, Property> campos(Object... nomesETipos) {
        Map<String, Property> m = new LinkedHashMap<>();
        for (int i = 0; i < nomesETipos.length; i += 2) {
            m.put((String) nomesETipos[i], (Property) nomesETipos[i + 1]);
        }
        return m;
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

    private static Property inteiro() {
        return Property.of(p -> p.integer(i -> i));
    }

    private static Property longo() {
        return Property.of(p -> p.long_(l -> l));
    }

    private static Property data() {
        return Property.of(p -> p.date(d -> d.format("strict_date")));
    }

    private static Property nested(Map<String, Property> campos) {
        return Property.of(p -> p.nested(n -> n.properties(campos)));
    }

    private static Property objeto(Map<String, Property> campos) {
        return Property.of(p -> p.object(o -> o.properties(campos)));
    }
}
