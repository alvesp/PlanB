package pt.karate.aprendizagem.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.karate.aprendizagem.app.AlunoAprendizagemService;
import pt.karate.aprendizagem.app.ResultadoComando;

import java.util.UUID;

/**
 * Comandos sobre a aprendizagem de um aluno (o id é o mesmo do utentes-service).
 * Progresso, elegibilidade, presenças, temas, avaliações e exames leem-se em
 * {@code /api/consulta/progresso/{alunoId}/...} no utentes-query-service.
 */
@RestController
@RequestMapping("/api/alunos/{alunoId}")
public class AlunoAprendizagemController {

    private final AlunoAprendizagemService service;

    public AlunoAprendizagemController(AlunoAprendizagemService service) {
        this.service = service;
    }

    @PostMapping("/avaliacoes")
    @ResponseStatus(HttpStatus.CREATED)
    public ResultadoComando avaliar(@PathVariable UUID alunoId, @Valid @RequestBody Pedidos.AvaliacaoRequest pedido) {
        return service.avaliar(alunoId, pedido.comando());
    }

    /**
     * Regista um exame de graduação. Use {@code dispensarRequisitos} (com motivo) para
     * reconhecer uma graduação obtida noutro clube ou saltar graduações.
     */
    @PostMapping("/exames")
    @ResponseStatus(HttpStatus.CREATED)
    public ResultadoComando registarExame(@PathVariable UUID alunoId, @Valid @RequestBody Pedidos.ExameRequest pedido) {
        return service.registarExame(alunoId, pedido.comando());
    }
}
