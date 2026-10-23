package pt.karate.utentes.consulta.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.karate.aprendizagem.events.AulaSnapshot;
import pt.karate.aprendizagem.events.GraduacaoCatalogoSnapshot;
import pt.karate.aprendizagem.events.ProgressoAlunoSnapshot;
import pt.karate.aprendizagem.events.TemaSnapshot;
import pt.karate.utentes.consulta.api.ConsultaAprendizagemService.Elegibilidade;
import pt.karate.utentes.consulta.api.ConsultaAprendizagemService.FolhaPresencas;
import pt.karate.utentes.consulta.api.ConsultaAprendizagemService.RegistoPresenca;
import pt.karate.utentes.consulta.api.ConsultaAprendizagemService.TemaAluno;
import pt.karate.utentes.consulta.api.ConsultaAprendizagemService.TurmaView;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Consultas de aprendizagem (lado de leitura do CQRS): catálogo, turmas, aulas e presenças,
 * e progresso dos alunos. Os comandos correspondentes estão no aprendizagem-service.
 */
@RestController
@RequestMapping("/api/consulta")
@Validated
public class ConsultaAprendizagemController {

    private final ConsultaAprendizagemService service;

    public ConsultaAprendizagemController(ConsultaAprendizagemService service) {
        this.service = service;
    }

    // ---- Catálogo ----

    @GetMapping("/graduacoes")
    public List<GraduacaoCatalogoSnapshot> graduacoes() {
        return service.graduacoes();
    }

    @GetMapping("/graduacoes/{codigo}")
    public ResponseEntity<GraduacaoCatalogoSnapshot> graduacao(@PathVariable String codigo) {
        return ResponseEntity.of(service.graduacao(codigo));
    }

    /** Temas do programa de exame de uma graduação. */
    @GetMapping("/graduacoes/{codigo}/programa")
    public List<TemaSnapshot> programa(@PathVariable String codigo) {
        return service.programa(codigo);
    }

    @GetMapping("/temas")
    public List<TemaSnapshot> temas(@RequestParam(required = false) String q,
                                    @RequestParam(required = false) String categoria,
                                    @RequestParam(required = false) String graduacao,
                                    @RequestParam(defaultValue = "false") boolean incluirInativos) {
        return service.temas(q, categoria, graduacao, incluirInativos);
    }

    @GetMapping("/temas/{id}")
    public ResponseEntity<TemaSnapshot> tema(@PathVariable UUID id) {
        return ResponseEntity.of(service.tema(id));
    }

    // ---- Turmas e aulas ----

    @GetMapping("/turmas")
    public List<TurmaView> turmas(@RequestParam(defaultValue = "false") boolean incluirInativas) {
        return service.turmas(incluirInativas);
    }

    @GetMapping("/turmas/{id}")
    public ResponseEntity<TurmaView> turma(@PathVariable UUID id) {
        return ResponseEntity.of(service.turma(id));
    }

    /** Por omissão devolve as aulas entre um mês antes e um mês depois de hoje. */
    @GetMapping("/aulas")
    public List<AulaSnapshot> aulas(
            @RequestParam(required = false) UUID turmaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.aulas(turmaId, de, ate);
    }

    @GetMapping("/aulas/{id}")
    public ResponseEntity<AulaSnapshot> aula(@PathVariable UUID id) {
        return ResponseEntity.of(service.aula(id));
    }

    /** Folha de presenças: alunos da turma com o estado registado (nulo = por registar). */
    @GetMapping("/aulas/{id}/presencas")
    public ResponseEntity<FolhaPresencas> folha(@PathVariable UUID id) {
        return ResponseEntity.of(service.folha(id));
    }

    // ---- Progresso ----

    @GetMapping("/progresso")
    public Pagina<ProgressoAlunoSnapshot> pesquisarProgresso(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String graduacao,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semPresencaDesde,
            @RequestParam(defaultValue = "0") @Min(0) @Max(500) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        ConsultaController.verificarJanela(pagina, tamanho);
        return service.pesquisarProgresso(
                new ConsultaAprendizagemService.FiltroProgresso(q, graduacao, semPresencaDesde, pagina, tamanho));
    }

    /** Graduação atual e seguinte, presenças, exames, avaliações e prática por tema. */
    @GetMapping("/progresso/{alunoId}")
    public ResponseEntity<ProgressoAlunoSnapshot> progresso(@PathVariable UUID alunoId) {
        return ResponseEntity.of(service.progresso(alunoId));
    }

    @GetMapping("/progresso/{alunoId}/elegibilidade")
    public ResponseEntity<Elegibilidade> elegibilidade(@PathVariable UUID alunoId) {
        return ResponseEntity.of(service.elegibilidade(alunoId));
    }

    /** Temas da próxima graduação (ou todos) com nível de domínio e prática nas aulas. */
    @GetMapping("/progresso/{alunoId}/temas")
    public ResponseEntity<List<TemaAluno>> temas(@PathVariable UUID alunoId,
                                                 @RequestParam(defaultValue = "false") boolean todos) {
        return ResponseEntity.of(service.temasDoAluno(alunoId, todos));
    }

    /** Registo de presenças (por omissão, últimos 3 meses). */
    @GetMapping("/progresso/{alunoId}/presencas")
    public List<RegistoPresenca> presencas(
            @PathVariable UUID alunoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.presencasDoAluno(alunoId, de, ate);
    }

    @GetMapping("/progresso/{alunoId}/exames")
    public ResponseEntity<List<ProgressoAlunoSnapshot.Exame>> exames(@PathVariable UUID alunoId) {
        return ResponseEntity.of(service.progresso(alunoId).map(ProgressoAlunoSnapshot::exames));
    }

    @GetMapping("/progresso/{alunoId}/avaliacoes")
    public ResponseEntity<List<ProgressoAlunoSnapshot.Avaliacao>> avaliacoes(@PathVariable UUID alunoId) {
        return ResponseEntity.of(service.progresso(alunoId).map(ProgressoAlunoSnapshot::avaliacoes));
    }
}
