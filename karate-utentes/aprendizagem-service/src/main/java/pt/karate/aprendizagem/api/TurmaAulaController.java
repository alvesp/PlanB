package pt.karate.aprendizagem.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.karate.aprendizagem.app.AulaService;
import pt.karate.aprendizagem.app.ResultadoComando;
import pt.karate.aprendizagem.app.TurmaService;

import java.util.UUID;

/**
 * Comandos sobre turmas, aulas e presenças. Consultas (incluindo a folha de presenças)
 * em {@code /api/consulta/turmas} e {@code /api/consulta/aulas} no utentes-query-service.
 */
@RestController
@RequestMapping("/api")
public class TurmaAulaController {

    private final TurmaService turmas;
    private final AulaService aulas;

    public TurmaAulaController(TurmaService turmas, AulaService aulas) {
        this.turmas = turmas;
        this.aulas = aulas;
    }

    @PostMapping("/turmas")
    @ResponseStatus(HttpStatus.CREATED)
    public ResultadoComando criarTurma(@Valid @RequestBody Pedidos.TurmaRequest pedido) {
        return turmas.criar(pedido.dados());
    }

    @PutMapping("/turmas/{id}")
    public ResultadoComando atualizarTurma(@PathVariable UUID id, @Valid @RequestBody Pedidos.TurmaRequest pedido) {
        return turmas.atualizar(id, pedido.dados());
    }

    @PutMapping("/turmas/{id}/alunos/{alunoId}")
    public ResultadoComando adicionarAluno(@PathVariable UUID id, @PathVariable UUID alunoId) {
        return turmas.adicionarAluno(id, alunoId);
    }

    @DeleteMapping("/turmas/{id}/alunos/{alunoId}")
    public ResultadoComando removerAluno(@PathVariable UUID id, @PathVariable UUID alunoId) {
        return turmas.removerAluno(id, alunoId);
    }

    @PostMapping("/aulas")
    @ResponseStatus(HttpStatus.CREATED)
    public ResultadoComando criarAula(@Valid @RequestBody Pedidos.AulaRequest pedido) {
        return aulas.criar(pedido.dados());
    }

    @PutMapping("/aulas/{id}")
    public ResultadoComando atualizarAula(@PathVariable UUID id, @Valid @RequestBody Pedidos.AulaRequest pedido) {
        return aulas.atualizar(id, pedido.dados());
    }

    @PostMapping("/aulas/{id}/cancelar")
    public ResultadoComando cancelarAula(@PathVariable UUID id) {
        return aulas.cancelar(id);
    }

    /** Regista ou corrige presenças (em lote, tudo ou nada). */
    @PutMapping("/aulas/{id}/presencas")
    public ResultadoComando registarPresencas(@PathVariable UUID id,
                                              @Valid @RequestBody Pedidos.PresencasRequest pedido) {
        return aulas.registarPresencas(id, pedido.registos().stream().map(Pedidos.PresencaRequest::registo).toList());
    }
}
