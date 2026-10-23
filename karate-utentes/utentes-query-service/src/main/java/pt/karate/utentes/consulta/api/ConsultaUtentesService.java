package pt.karate.utentes.consulta.api;

import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.stereotype.Service;
import pt.karate.utentes.consulta.indices.Indices;
import pt.karate.utentes.events.AlunoSnapshot;
import pt.karate.utentes.events.PessoaSnapshot;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

@Service
/** Consultas de utentes: alunos, pessoas e responsável fiscal. */
public class ConsultaUtentesService {

    public record FiltroAlunos(String texto, String estado, String graduacao, Boolean menor,
                               String responsavelId, int pagina, int tamanho) {
    }

    public record FiltroPessoas(String texto, String nif, int pagina, int tamanho) {
    }

    private static final int MAIORIDADE = 18;

    private final LeituraOpenSearch leitura;
    private final Clock clock;

    public ConsultaUtentesService(LeituraOpenSearch leitura, Clock clock) {
        this.leitura = leitura;
        this.clock = clock;
    }

    public Optional<AlunoSnapshot> aluno(String id) {
        return leitura.obter(Indices.ALUNOS, id, AlunoSnapshot.class);
    }

    public Optional<PessoaSnapshot> pessoa(String id) {
        return leitura.obter(Indices.PESSOAS, id, PessoaSnapshot.class);
    }

    /** Quem é faturado pelas mensalidades do aluno. */
    public Optional<AlunoSnapshot.ResponsavelFiscalResumo> responsavelFiscal(String alunoId) {
        return aluno(alunoId).map(AlunoSnapshot::responsavelFiscal);
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
        return leitura.pagina(req.build(), AlunoSnapshot.class, f.pagina(), f.tamanho());
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
        return leitura.pagina(req.build(), PessoaSnapshot.class, f.pagina(), f.tamanho());
    }

    private static Query termo(String campo, String valor) {
        return Query.of(q -> q.term(t -> t.field(campo).value(FieldValue.of(valor))));
    }

    private static boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }
}
