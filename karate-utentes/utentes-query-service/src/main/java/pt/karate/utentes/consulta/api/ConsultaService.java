package pt.karate.utentes.consulta.api;

import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.stereotype.Service;
import pt.karate.utentes.consulta.indices.Indices;
import pt.karate.utentes.events.AlunoSnapshot;
import pt.karate.utentes.events.PessoaSnapshot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

@Service
public class ConsultaService {

    public record FiltroAlunos(String texto, String estado, String graduacao, Boolean menor,
                               String responsavelId, int pagina, int tamanho) {
    }

    public record FiltroPessoas(String texto, String nif, int pagina, int tamanho) {
    }

    private static final int MAIORIDADE = 18;

    private final OpenSearchClient client;
    private final Clock clock;

    public ConsultaService(OpenSearchClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    public Optional<AlunoSnapshot> aluno(String id) {
        return obter(Indices.ALUNOS, id, AlunoSnapshot.class);
    }

    public Optional<PessoaSnapshot> pessoa(String id) {
        return obter(Indices.PESSOAS, id, PessoaSnapshot.class);
    }

    /**
     * Pesquisa livre por nome do aluno, nome de um responsável, número de federação ou NIF
     * do responsável fiscal, com filtros por estado, graduação, idade e responsável.
     */
    public Pagina<AlunoSnapshot> pesquisarAlunos(FiltroAlunos f) {
        BoolQuery.Builder bool = new BoolQuery.Builder();

        if (temTexto(f.texto())) {
            String t = f.texto().strip();
            bool.must(m -> m.bool(b -> b
                    .should(s -> s.match(mt -> mt.field("nome").query(FieldValue.of(t)).fuzziness("AUTO").boost(3f)))
                    .should(s -> s.nested(n -> n
                            .path("responsaveis")
                            .scoreMode(ChildScoreMode.Max)
                            .query(q -> q.match(mt -> mt.field("responsaveis.nome").query(FieldValue.of(t))))))
                    .should(s -> s.term(te -> te.field("numFederacao").value(FieldValue.of(t))))
                    .should(s -> s.term(te -> te.field("responsavelFiscal.nif").value(FieldValue.of(t))))
                    .minimumShouldMatch("1")));
        }
        if (temTexto(f.estado())) {
            bool.filter(termo("estado", f.estado()));
        }
        if (temTexto(f.graduacao())) {
            bool.filter(termo("graduacaoAtual", f.graduacao()));
        }
        if (f.menor() != null) {
            // Menor = nasceu depois de (hoje - 18 anos).
            String limite = LocalDate.now(clock).minusYears(MAIORIDADE).toString();
            bool.filter(q -> q.range(r -> {
                r.field("dataNascimento");
                return f.menor() ? r.gt(JsonData.of(limite)) : r.lte(JsonData.of(limite));
            }));
        }
        if (temTexto(f.responsavelId())) {
            bool.filter(q -> q.nested(n -> n
                    .path("responsaveis")
                    .query(termo("responsaveis.pessoaId", f.responsavelId()))));
        }

        SearchRequest.Builder req = new SearchRequest.Builder()
                .index(Indices.ALUNOS)
                .query(q -> q.bool(bool.build()))
                .from(f.pagina() * f.tamanho())
                .size(f.tamanho())
                .trackTotalHits(t -> t.enabled(true));
        if (!temTexto(f.texto())) {
            req.sort(s -> s.field(fs -> fs.field("nome.keyword").order(SortOrder.Asc)));
        }
        return pesquisar(req.build(), AlunoSnapshot.class, f.pagina(), f.tamanho());
    }

    public Pagina<PessoaSnapshot> pesquisarPessoas(FiltroPessoas f) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (temTexto(f.texto())) {
            String t = f.texto().strip();
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(t)
                    .fields("nome^3", "nomeFaturacao")
                    .fuzziness("AUTO")));
        }
        if (temTexto(f.nif())) {
            bool.filter(termo("nif", f.nif().strip()));
        }
        SearchRequest.Builder req = new SearchRequest.Builder()
                .index(Indices.PESSOAS)
                .query(q -> q.bool(bool.build()))
                .from(f.pagina() * f.tamanho())
                .size(f.tamanho())
                .trackTotalHits(t -> t.enabled(true));
        if (!temTexto(f.texto())) {
            req.sort(s -> s.field(fs -> fs.field("nome.keyword").order(SortOrder.Asc)));
        }
        return pesquisar(req.build(), PessoaSnapshot.class, f.pagina(), f.tamanho());
    }

    private <T> Pagina<T> pesquisar(SearchRequest req, Class<T> tipo, int pagina, int tamanho) {
        try {
            SearchResponse<T> resp = client.search(req, tipo);
            long total = resp.hits().total() == null ? 0 : resp.hits().total().value();
            return new Pagina<>(
                    resp.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).toList(),
                    total, pagina, tamanho);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private <T> Optional<T> obter(String indice, String id, Class<T> tipo) {
        try {
            var resp = client.get(g -> g.index(indice).id(id), tipo);
            return resp.found() ? Optional.ofNullable(resp.source()) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Query termo(String campo, String valor) {
        return Query.of(q -> q.term(t -> t.field(campo).value(FieldValue.of(valor))));
    }

    private static boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }
}
