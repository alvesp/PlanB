package pt.karate.utentes.consulta.api;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.stereotype.Component;
import pt.karate.utentes.consulta.indices.Indices;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Operações de leitura comuns sobre o OpenSearch. */
@Component
public class LeituraOpenSearch {

    /** Dados mínimos de um aluno, para juntar nomes a turmas e folhas de presenças. */
    public record AlunoNome(UUID alunoId, String nome, String estado) {
    }

    private final OpenSearchClient client;

    public LeituraOpenSearch(OpenSearchClient client) {
        this.client = client;
    }

    public <T> Optional<T> obter(String indice, String id, Class<T> tipo) {
        try {
            var resp = client.get(g -> g.index(indice).id(id), tipo);
            return resp.found() ? Optional.ofNullable(resp.source()) : Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public <T> Pagina<T> pagina(SearchRequest req, Class<T> tipo, int pagina, int tamanho) {
        SearchResponse<T> resp = pesquisar(req, tipo);
        long total = resp.hits().total() == null ? 0 : resp.hits().total().value();
        return new Pagina<>(fontes(resp), total, pagina, tamanho);
    }

    public <T> List<T> lista(SearchRequest req, Class<T> tipo) {
        return fontes(pesquisar(req, tipo));
    }

    /** Nomes atuais dos alunos (índice de alunos), para não depender de cópias desatualizadas. */
    public Map<UUID, AlunoNome> nomesDeAlunos(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            var resp = client.mget(m -> m
                    .index(Indices.ALUNOS)
                    .ids(ids.stream().map(UUID::toString).toList())
                    .sourceIncludes(List.of("alunoId", "nome", "estado")), AlunoNome.class);
            return resp.docs().stream()
                    .filter(d -> d.isResult() && d.result().found() && d.result().source() != null)
                    .map(d -> d.result().source())
                    .collect(Collectors.toMap(AlunoNome::alunoId, Function.identity(), (a, b) -> a));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private <T> SearchResponse<T> pesquisar(SearchRequest req, Class<T> tipo) {
        try {
            return client.search(req, tipo);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static <T> List<T> fontes(SearchResponse<T> resp) {
        return resp.hits().hits().stream().map(Hit::source).filter(Objects::nonNull).toList();
    }
}
