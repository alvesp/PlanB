package pt.karate.utentes.consulta.projecao;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.VersionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.karate.utentes.consulta.indices.Indices;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Escreve snapshots no OpenSearch com versionamento externo.
 *
 * <p>O OpenSearch só aceita a escrita se {@code versao} for maior do que a guardada.
 * Assim, um evento repetido (entrega at-least-once) ou que chega atrasado é rejeitado
 * com 409 e ignorado, e o índice converge sempre para o snapshot mais recente.
 */
@Component
public class Projecao {

    private static final Logger log = LoggerFactory.getLogger(Projecao.class);

    private final OpenSearchClient client;

    // A dependência de Indices garante que os índices existem antes de se projetar.
    public Projecao(OpenSearchClient client, Indices indices) {
        this.client = client;
    }

    /** @return {@code true} se o documento foi escrito, {@code false} se era obsoleto/duplicado. */
    public boolean indexar(String indice, String id, long versao, Object documento) {
        try {
            client.index(i -> i
                    .index(indice)
                    .id(id)
                    .version(versao)
                    .versionType(VersionType.External)
                    .document(documento));
            return true;
        } catch (OpenSearchException e) {
            if (e.status() == 409) {
                log.debug("Ignorado {}/{} v{}: já existe versão igual ou superior", indice, id, versao);
                return false;
            }
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Tombstone no tópico compactado: remove o documento. */
    public void remover(String indice, String id) {
        try {
            client.delete(d -> d.index(indice).id(id));
        } catch (OpenSearchException e) {
            if (e.status() != 404) {
                throw e;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
