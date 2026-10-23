package pt.karate.utentes.consulta.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pt.karate.utentes.events.AlunoSnapshot;
import pt.karate.utentes.events.PessoaSnapshot;

import java.util.UUID;

/**
 * Consultas de utentes (lado de leitura do CQRS). Os dados são eventualmente consistentes:
 * o campo {@code versao} pode ser comparado com a versão devolvida pelo comando no
 * utentes-service para saber se a projeção já está atualizada.
 */
@RestController
@RequestMapping("/api/consulta")
@Validated
public class ConsultaController {

    private final ConsultaUtentesService service;

    public ConsultaController(ConsultaUtentesService service) {
        this.service = service;
    }

    @GetMapping("/alunos")
    public Pagina<AlunoSnapshot> pesquisarAlunos(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String graduacao,
            @RequestParam(required = false) Boolean menor,
            @RequestParam(required = false) UUID responsavelId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(500) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        verificarJanela(pagina, tamanho);
        return service.pesquisarAlunos(new ConsultaUtentesService.FiltroAlunos(
                q, estado, graduacao, menor,
                responsavelId == null ? null : responsavelId.toString(),
                pagina, tamanho));
    }

    /** Ficha do aluno: responsáveis vigentes e históricos, responsável fiscal e graduações. */
    @GetMapping("/alunos/{id}")
    public ResponseEntity<AlunoSnapshot> aluno(@PathVariable UUID id) {
        return ResponseEntity.of(service.aluno(id.toString()));
    }

    /** Quem é faturado pelas mensalidades do aluno. */
    @GetMapping("/alunos/{id}/responsavel-fiscal")
    public ResponseEntity<AlunoSnapshot.ResponsavelFiscalResumo> responsavelFiscal(@PathVariable UUID id) {
        return ResponseEntity.of(service.responsavelFiscal(id.toString()));
    }

    @GetMapping("/pessoas")
    public Pagina<PessoaSnapshot> pesquisarPessoas(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String nif,
            @RequestParam(defaultValue = "0") @Min(0) @Max(500) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        verificarJanela(pagina, tamanho);
        return service.pesquisarPessoas(new ConsultaUtentesService.FiltroPessoas(q, nif, pagina, tamanho));
    }

    @GetMapping("/pessoas/{id}")
    public ResponseEntity<PessoaSnapshot> pessoa(@PathVariable UUID id) {
        return ResponseEntity.of(service.pessoa(id.toString()));
    }

    /** O OpenSearch limita from + size a 10 000 resultados (index.max_result_window). */
    static void verificarJanela(int pagina, int tamanho) {
        if ((long) (pagina + 1) * tamanho > 10_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Paginação além dos 10 000 resultados; refine a pesquisa.");
        }
    }
}
