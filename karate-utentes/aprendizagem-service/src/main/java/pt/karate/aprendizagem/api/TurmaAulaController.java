package pt.karate.aprendizagem.api;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.karate.aprendizagem.app.AulaService;
import pt.karate.aprendizagem.app.TurmaService;
import pt.karate.aprendizagem.app.Vistas.AulaView;
import pt.karate.aprendizagem.app.Vistas.FolhaPresencas;
import pt.karate.aprendizagem.app.Vistas.TurmaView;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Turmas, aulas e registo de presenças. */
@RestController
@RequestMapping("/api")
public class TurmaAulaController {

    private final TurmaService turmas;
    private final AulaService aulas;

    public TurmaAulaController(TurmaService turmas, AulaService aulas) {
        this.turmas = turmas;
        this.aulas = aulas;
    }

    // ---- Turmas ----

    @GetMapping("/turmas")
    public List<TurmaView> turmas() {
        return turmas.listar();
    }

    @GetMapping("/turmas/{id}")
    public TurmaView turma(@PathVariable UUID id) {
        return turmas.obter(id);
    }

    @PostMapping("/turmas")
    @ResponseStatus(HttpStatus.CREATED)
    public TurmaView criarTurma(@Valid @RequestBody Pedidos.TurmaRequest pedido) {
        return turmas.criar(pedido.dados());
    }

    @PutMapping("/turmas/{id}")
    public TurmaView atualizarTurma(@PathVariable UUID id, @Valid @RequestBody Pedidos.TurmaRequest pedido) {
        return turmas.atualizar(id, pedido.dados());
    }

    @PutMapping("/turmas/{id}/alunos/{alunoId}")
    public TurmaView adicionarAluno(@PathVariable UUID id, @PathVariable UUID alunoId) {
        return turmas.adicionarAluno(id, alunoId);
    }

    @DeleteMapping("/turmas/{id}/alunos/{alunoId}")
    public TurmaView removerAluno(@PathVariable UUID id, @PathVariable UUID alunoId) {
        return turmas.removerAluno(id, alunoId);
    }

    // ---- Aulas ----

    /** Por omissão devolve as aulas entre um mês antes e um mês depois de hoje. */
    @GetMapping("/aulas")
    public List<AulaView> aulas(@RequestParam(required = false) UUID turmaId,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return aulas.pesquisar(turmaId, de, ate);
    }

    @GetMapping("/aulas/{id}")
    public AulaView aula(@PathVariable UUID id) {
        return aulas.obter(id);
    }

    @PostMapping("/aulas")
    @ResponseStatus(HttpStatus.CREATED)
    public AulaView criarAula(@Valid @RequestBody Pedidos.AulaRequest pedido) {
        return aulas.criar(pedido.dados());
    }

    /** Atualiza horário, sumário e temas trabalhados (a turma não muda). */
    @PutMapping("/aulas/{id}")
    public AulaView atualizarAula(@PathVariable UUID id, @Valid @RequestBody Pedidos.AulaRequest pedido) {
        return aulas.atualizar(id, pedido.dados());
    }

    @PostMapping("/aulas/{id}/cancelar")
    public AulaView cancelarAula(@PathVariable UUID id) {
        return aulas.cancelar(id);
    }

    /** Folha de presenças: alunos da turma com o estado registado (nulo = por registar). */
    @GetMapping("/aulas/{id}/presencas")
    public FolhaPresencas folha(@PathVariable UUID id) {
        return aulas.folha(id);
    }

    /** Regista ou corrige presenças (em lote, tudo ou nada). */
    @PutMapping("/aulas/{id}/presencas")
    public FolhaPresencas registarPresencas(@PathVariable UUID id,
                                            @Valid @RequestBody Pedidos.PresencasRequest pedido) {
        return aulas.registarPresencas(id, pedido.registos().stream().map(Pedidos.PresencaRequest::registo).toList());
    }
}
