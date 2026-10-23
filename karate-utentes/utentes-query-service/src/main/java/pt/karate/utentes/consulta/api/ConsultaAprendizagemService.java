package pt.karate.utentes.consulta.api;

import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.stereotype.Service;
import pt.karate.aprendizagem.events.AulaSnapshot;
import pt.karate.aprendizagem.events.GraduacaoCatalogoSnapshot;
import pt.karate.aprendizagem.events.ProgressoAlunoSnapshot;
import pt.karate.aprendizagem.events.TemaSnapshot;
import pt.karate.aprendizagem.events.TurmaSnapshot;
import pt.karate.utentes.consulta.api.LeituraOpenSearch.AlunoNome;
import pt.karate.utentes.consulta.indices.Indices;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Consultas de aprendizagem, todas sobre os modelos de leitura do OpenSearch.
 * O que depende da data de hoje (elegibilidade) é calculado aqui, a partir dos requisitos
 * publicados no snapshot de progresso.
 */
@Service
public class ConsultaAprendizagemService {

    /** Máximo de documentos devolvidos em listas sem paginação (catálogo, aulas de um período). */
    private static final int LIMITE_LISTA = 500;

    public record FiltroProgresso(String texto, String graduacao, LocalDate semPresencaDesde, int pagina, int tamanho) {
    }

    public record AlunoTurma(UUID alunoId, String nome, String estado) {
    }

    public record TurmaView(UUID turmaId, long versao, String nome, String descricao, String instrutor,
                            boolean ativa, List<AlunoTurma> alunos) {
    }

    /** Linha da folha de presenças: {@code estado} nulo = ainda não registado. */
    public record LinhaFolha(UUID alunoId, String nome, boolean membroTurma, String estado, String observacao) {
    }

    public record FolhaPresencas(UUID aulaId, long versao, LocalDate data, LocalTime horaInicio, UUID turmaId,
                                 String turma, boolean cancelada, List<AulaSnapshot.TemaResumo> temas,
                                 List<LinhaFolha> alunos) {
    }

    public record RegistoPresenca(UUID aulaId, LocalDate data, LocalTime horaInicio, String turma,
                                  String estado, String observacao) {
    }

    public record Elegibilidade(String proximaGraduacao, String proximaGraduacaoNome, boolean elegivel,
                                long presencas, int presencasMinimas, long mesesDecorridos, int mesesMinimos,
                                LocalDate cumpreTempoEm, int temasDominados, int temasPrograma,
                                List<String> pendentes) {
    }

    public record TemaAluno(UUID temaId, String nome, String categoria, String graduacaoCodigo,
                            String nivel, LocalDate dataAvaliacao, long vezesPraticado, LocalDate ultimaPratica) {
    }

    private final LeituraOpenSearch leitura;
    private final Clock clock;

    public ConsultaAprendizagemService(LeituraOpenSearch leitura, Clock clock) {
        this.leitura = leitura;
        this.clock = clock;
    }

    // ---- Catálogo ----

    public List<GraduacaoCatalogoSnapshot> graduacoes() {
        var req = SearchRequest.of(s -> s.index(Indices.GRADUACOES).size(LIMITE_LISTA)
                .sort(o -> o.field(f -> f.field("ordem").order(SortOrder.Asc))));
        return leitura.lista(req, GraduacaoCatalogoSnapshot.class);
    }

    public Optional<GraduacaoCatalogoSnapshot> graduacao(String codigo) {
        return leitura.obter(Indices.GRADUACOES, codigo, GraduacaoCatalogoSnapshot.class);
    }

    /** Temas do programa de exame de uma graduação (só ativos). */
    public List<TemaSnapshot> programa(String codigoGraduacao) {
        return temas(null, null, codigoGraduacao, false);
    }

    public List<TemaSnapshot> temas(String texto, String categoria, String graduacao, boolean incluirInativos) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (temTexto(texto)) {
            bool.must(m -> m.match(mt -> mt.field("nome").query(FieldValue.of(texto.strip())).fuzziness("AUTO")));
        }
        if (temTexto(categoria)) {
            bool.filter(termo("categoria", categoria));
        }
        if (temTexto(graduacao)) {
            bool.filter(termo("graduacaoCodigo", graduacao));
        }
        if (!incluirInativos) {
            bool.filter(q -> q.term(t -> t.field("ativo").value(FieldValue.of(true))));
        }
        var req = SearchRequest.of(s -> s.index(Indices.TEMAS).size(LIMITE_LISTA)
                .query(q -> q.bool(bool.build()))
                .sort(o -> o.field(f -> f.field("categoria").order(SortOrder.Asc)))
                .sort(o -> o.field(f -> f.field("nome.keyword").order(SortOrder.Asc))));
        return leitura.lista(req, TemaSnapshot.class);
    }

    public Optional<TemaSnapshot> tema(UUID id) {
        return leitura.obter(Indices.TEMAS, id.toString(), TemaSnapshot.class);
    }

    // ---- Turmas e aulas ----

    public List<TurmaView> turmas(boolean incluirInativas) {
        var req = SearchRequest.of(s -> s.index(Indices.TURMAS).size(LIMITE_LISTA)
                .query(q -> incluirInativas
                        ? q.matchAll(m -> m)
                        : q.term(t -> t.field("ativa").value(FieldValue.of(true))))
                .sort(o -> o.field(f -> f.field("nome.keyword").order(SortOrder.Asc))));
        List<TurmaSnapshot> turmas = leitura.lista(req, TurmaSnapshot.class);
        Set<UUID> todos = turmas.stream().flatMap(t -> t.alunos().stream()).collect(Collectors.toSet());
        Map<UUID, AlunoNome> nomes = leitura.nomesDeAlunos(todos);
        return turmas.stream().map(t -> vista(t, nomes)).toList();
    }

    public Optional<TurmaView> turma(UUID id) {
        return leitura.obter(Indices.TURMAS, id.toString(), TurmaSnapshot.class)
                .map(t -> vista(t, leitura.nomesDeAlunos(t.alunos())));
    }

    /** Aulas de um período (por omissão, de um mês antes a um mês depois de hoje). */
    public List<AulaSnapshot> aulas(UUID turmaId, LocalDate de, LocalDate ate) {
        LocalDate inicio = de != null ? de : hoje().minusMonths(1);
        LocalDate fim = ate != null ? ate : hoje().plusMonths(1);
        BoolQuery.Builder bool = new BoolQuery.Builder()
                .filter(q -> q.range(r -> r.field("data").gte(JsonData.of(inicio.toString())).lte(JsonData.of(fim.toString()))));
        if (turmaId != null) {
            bool.filter(termo("turmaId", turmaId.toString()));
        }
        var req = SearchRequest.of(s -> s.index(Indices.AULAS).size(LIMITE_LISTA)
                .query(q -> q.bool(bool.build()))
                .sort(o -> o.field(f -> f.field("data").order(SortOrder.Asc)))
                .sort(o -> o.field(f -> f.field("horaInicio").order(SortOrder.Asc))));
        return leitura.lista(req, AulaSnapshot.class);
    }

    public Optional<AulaSnapshot> aula(UUID id) {
        return leitura.obter(Indices.AULAS, id.toString(), AulaSnapshot.class);
    }

    /**
     * Folha de presenças: alunos da turma (registados ou não) mais quem foi registado sem
     * pertencer à turma (ex.: aluno de outra turma que veio treinar).
     */
    public Optional<FolhaPresencas> folha(UUID aulaId) {
        return aula(aulaId).map(aula -> {
            List<UUID> membros = aula.turmaId() == null ? List.of()
                    : leitura.obter(Indices.TURMAS, aula.turmaId().toString(), TurmaSnapshot.class)
                    .map(TurmaSnapshot::alunos).orElse(List.of());
            Map<UUID, AulaSnapshot.PresencaRegistada> registadas = aula.presencas().stream()
                    .collect(Collectors.toMap(AulaSnapshot.PresencaRegistada::alunoId, Function.identity(), (a, b) -> a));

            Set<UUID> todos = new LinkedHashSet<>(membros);
            todos.addAll(registadas.keySet());
            Map<UUID, AlunoNome> nomes = leitura.nomesDeAlunos(todos);

            List<LinhaFolha> linhas = new ArrayList<>();
            for (UUID alunoId : todos) {
                var p = registadas.get(alunoId);
                AlunoNome n = nomes.get(alunoId);
                linhas.add(new LinhaFolha(alunoId, n == null ? null : n.nome(), membros.contains(alunoId),
                        p == null ? null : p.estado(), p == null ? null : p.observacao()));
            }
            linhas.sort(Comparator.comparing(LinhaFolha::nome, Comparator.nullsLast(Comparator.naturalOrder())));
            return new FolhaPresencas(aula.aulaId(), aula.versao(), aula.data(), aula.horaInicio(), aula.turmaId(),
                    aula.turma(), aula.cancelada(), aula.temas(), linhas);
        });
    }

    // ---- Progresso dos alunos ----

    public Optional<ProgressoAlunoSnapshot> progresso(UUID alunoId) {
        return leitura.obter(Indices.PROGRESSO, alunoId.toString(), ProgressoAlunoSnapshot.class);
    }

    /** Progresso dos alunos: por nome, graduação atual e assiduidade (ex.: quem não treina há um mês). */
    public Pagina<ProgressoAlunoSnapshot> pesquisarProgresso(FiltroProgresso f) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (temTexto(f.texto())) {
            String t = f.texto().strip();
            bool.must(m -> m.match(mt -> mt.field("nome").query(FieldValue.of(t)).fuzziness("AUTO")));
        }
        if (temTexto(f.graduacao())) {
            bool.filter(termo("graduacaoAtual.codigo", f.graduacao().strip()));
        }
        if (f.semPresencaDesde() != null) {
            String limite = f.semPresencaDesde().toString();
            bool.filter(q -> q.bool(b -> b
                    .should(s -> s.range(r -> r.field("ultimaPresenca").lt(JsonData.of(limite))))
                    .should(s -> s.bool(n -> n.mustNot(e -> e.exists(x -> x.field("ultimaPresenca")))))
                    .minimumShouldMatch("1")));
        }
        SearchRequest.Builder req = new SearchRequest.Builder()
                .index(Indices.PROGRESSO)
                .query(q -> q.bool(bool.build()))
                .from(f.pagina() * f.tamanho())
                .size(f.tamanho())
                .trackTotalHits(t -> t.enabled(true));
        if (!temTexto(f.texto())) {
            req.sort(s -> s.field(fs -> fs.field("nome.keyword").order(SortOrder.Asc)));
        }
        return leitura.pagina(req.build(), ProgressoAlunoSnapshot.class, f.pagina(), f.tamanho());
    }

    /** Elegibilidade para a próxima graduação, calculada à data de hoje. */
    public Optional<Elegibilidade> elegibilidade(UUID alunoId) {
        return progresso(alunoId).map(this::elegibilidade);
    }

    Elegibilidade elegibilidade(ProgressoAlunoSnapshot p) {
        var req = p.requisitos();
        var proxima = p.proximaGraduacao();
        if (req == null || proxima == null) {
            return new Elegibilidade(null, null, false, p.presencasDesdeUltimaGraduacao(), 0, 0, 0, null, 0, 0,
                    List.of("Não existe graduação seguinte no catálogo."));
        }
        LocalDate hoje = hoje();
        long meses = req.referencia() == null ? 0 : Math.max(0, ChronoUnit.MONTHS.between(req.referencia(), hoje));
        List<String> pendentes = new ArrayList<>();
        if (p.presencasDesdeUltimaGraduacao() < req.presencasMinimas()) {
            pendentes.add("Presenças: " + p.presencasDesdeUltimaGraduacao() + " de " + req.presencasMinimas()
                    + " necessárias.");
        }
        if (req.cumpreTempoEm() != null && hoje.isBefore(req.cumpreTempoEm())) {
            pendentes.add("Tempo: " + meses + " de " + req.mesesMinimos() + " meses necessários (cumpre a "
                    + req.cumpreTempoEm() + ").");
        }
        return new Elegibilidade(proxima.codigo(), proxima.nome(), pendentes.isEmpty(),
                p.presencasDesdeUltimaGraduacao(), req.presencasMinimas(), meses, req.mesesMinimos(),
                req.cumpreTempoEm(), req.temasDominados(), req.temasPrograma(), List.copyOf(pendentes));
    }

    /**
     * Temas na perspetiva do aluno: por omissão o programa da próxima graduação; com
     * {@code todos=true}, todos os temas ativos. Junta a última avaliação e a prática nas aulas.
     */
    public Optional<List<TemaAluno>> temasDoAluno(UUID alunoId, boolean todos) {
        return progresso(alunoId).map(p -> {
            List<TemaSnapshot> lista;
            if (todos) {
                lista = temas(null, null, null, false);
            } else if (p.proximaGraduacao() != null) {
                lista = programa(p.proximaGraduacao().codigo());
            } else {
                lista = List.of();
            }
            Map<UUID, ProgressoAlunoSnapshot.TemaAvaliado> avaliacoes = p.temas().stream()
                    .collect(Collectors.toMap(ProgressoAlunoSnapshot.TemaAvaliado::temaId, Function.identity(), (a, b) -> a));
            Map<UUID, ProgressoAlunoSnapshot.Pratica> pratica = p.pratica().stream()
                    .collect(Collectors.toMap(ProgressoAlunoSnapshot.Pratica::temaId, Function.identity(), (a, b) -> a));
            return lista.stream().map(t -> {
                var a = avaliacoes.get(t.temaId());
                var pr = pratica.get(t.temaId());
                return new TemaAluno(t.temaId(), t.nome(), t.categoria(), t.graduacaoCodigo(),
                        a == null ? null : a.nivel(), a == null ? null : a.data(),
                        pr == null ? 0 : pr.vezes(), pr == null ? null : pr.ultimaVez());
            }).toList();
        });
    }

    /** Registo de presenças de um aluno (por omissão, últimos 3 meses), a partir do índice de aulas. */
    public List<RegistoPresenca> presencasDoAluno(UUID alunoId, LocalDate de, LocalDate ate) {
        LocalDate fim = ate != null ? ate : hoje();
        LocalDate inicio = de != null ? de : fim.minusMonths(3);
        String id = alunoId.toString();
        var req = SearchRequest.of(s -> s.index(Indices.AULAS).size(LIMITE_LISTA)
                .query(q -> q.bool(b -> b
                        .filter(f -> f.range(r -> r.field("data")
                                .gte(JsonData.of(inicio.toString())).lte(JsonData.of(fim.toString()))))
                        .filter(f -> f.nested(n -> n.path("presencas")
                                .query(termo("presencas.alunoId", id))))))
                .sort(o -> o.field(f -> f.field("data").order(SortOrder.Desc)))
                .sort(o -> o.field(f -> f.field("horaInicio").order(SortOrder.Desc))));
        return leitura.lista(req, AulaSnapshot.class).stream()
                .flatMap(a -> a.presencas().stream()
                        .filter(p -> p.alunoId().equals(alunoId))
                        .map(p -> new RegistoPresenca(a.aulaId(), a.data(), a.horaInicio(), a.turma(),
                                p.estado(), p.observacao())))
                .toList();
    }

    private TurmaView vista(TurmaSnapshot t, Map<UUID, AlunoNome> nomes) {
        List<AlunoTurma> alunos = t.alunos().stream()
                .map(id -> {
                    AlunoNome n = nomes.get(id);
                    return new AlunoTurma(id, n == null ? null : n.nome(), n == null ? null : n.estado());
                })
                .sorted(Comparator.comparing(AlunoTurma::nome, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return new TurmaView(t.turmaId(), t.versao(), t.nome(), t.descricao(), t.instrutor(), t.ativa(), alunos);
    }

    private LocalDate hoje() {
        return LocalDate.now(clock);
    }

    private static Query termo(String campo, String valor) {
        return Query.of(q -> q.term(t -> t.field(campo).value(FieldValue.of(valor))));
    }

    private static boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }
}
