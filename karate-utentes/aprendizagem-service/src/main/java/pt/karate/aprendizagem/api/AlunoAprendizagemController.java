package pt.karate.aprendizagem.api;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.karate.aprendizagem.app.AlunoAprendizagemService;
import pt.karate.aprendizagem.app.Vistas.AvaliacaoView;
import pt.karate.aprendizagem.app.Vistas.ElegibilidadeView;
import pt.karate.aprendizagem.app.Vistas.ExameView;
import pt.karate.aprendizagem.app.Vistas.ProgressoView;
import pt.karate.aprendizagem.app.Vistas.TemaAlunoView;
import pt.karate.aprendizagem.repo.RegistoPresenca;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Aprendizagem de um aluno (o id é o mesmo do utentes-service). */
@RestController
@RequestMapping("/api/alunos/{alunoId}")
public class AlunoAprendizagemController {

    private final AlunoAprendizagemService service;

    public AlunoAprendizagemController(AlunoAprendizagemService service) {
        this.service = service;
    }

    /** Graduação atual e seguinte, presenças, elegibilidade, exames e avaliações atuais. */
    @GetMapping("/progresso")
    public ProgressoView progresso(@PathVariable UUID alunoId) {
        return service.progresso(alunoId);
    }

    @GetMapping("/elegibilidade")
    public ElegibilidadeView elegibilidade(@PathVariable UUID alunoId) {
        return service.elegibilidade(alunoId);
    }

    /** Registo de presenças (por omissão, últimos 3 meses). */
    @GetMapping("/presencas")
    public List<RegistoPresenca> presencas(
            @PathVariable UUID alunoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.presencas(alunoId, de, ate);
    }

    /** Temas do programa da próxima graduação (ou todos) com avaliação e prática nas aulas. */
    @GetMapping("/temas")
    public List<TemaAlunoView> temas(@PathVariable UUID alunoId,
                                     @RequestParam(defaultValue = "false") boolean todos) {
        return service.temas(alunoId, todos);
    }

    @GetMapping("/avaliacoes")
    public List<AvaliacaoView> avaliacoes(@PathVariable UUID alunoId) {
        return service.avaliacoes(alunoId);
    }

    @PostMapping("/avaliacoes")
    @ResponseStatus(HttpStatus.CREATED)
    public AvaliacaoView avaliar(@PathVariable UUID alunoId, @Valid @RequestBody Pedidos.AvaliacaoRequest pedido) {
        return service.avaliar(alunoId, pedido.comando());
    }

    @GetMapping("/exames")
    public List<ExameView> exames(@PathVariable UUID alunoId) {
        return service.exames(alunoId);
    }

    /**
     * Regista um exame de graduação. Use {@code dispensarRequisitos} (com motivo) para
     * reconhecer uma graduação obtida noutro clube ou saltar graduações.
     */
    @PostMapping("/exames")
    @ResponseStatus(HttpStatus.CREATED)
    public ExameView registarExame(@PathVariable UUID alunoId, @Valid @RequestBody Pedidos.ExameRequest pedido) {
        return service.registarExame(alunoId, pedido.comando());
    }
}
