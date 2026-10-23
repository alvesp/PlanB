package pt.karate.utentes.api;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pt.karate.utentes.app.AlunoService;
import pt.karate.utentes.app.Vistas.AlunoView;
import pt.karate.utentes.app.Vistas.ResponsavelFiscalView;

import java.util.UUID;

@RestController
@RequestMapping("/api/alunos")
public class AlunoController {

    private final AlunoService service;

    public AlunoController(AlunoService service) {
        this.service = service;
    }

    /** Inscreve um aluno já com os responsáveis (exatamente um tem de ser responsável fiscal). */
    @PostMapping
    public ResponseEntity<AlunoView> inscrever(@Valid @RequestBody Pedidos.InscricaoRequest pedido) {
        AlunoView aluno = service.inscrever(pedido.paraComando());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(aluno.id()).toUri();
        return ResponseEntity.created(location).body(aluno);
    }

    @GetMapping("/{id}")
    public AlunoView obter(@PathVariable UUID id,
                           @RequestParam(defaultValue = "false") boolean historico) {
        return service.obter(id, historico);
    }

    @PutMapping("/{id}")
    public AlunoView atualizarDados(@PathVariable UUID id, @Valid @RequestBody Pedidos.DadosAlunoRequest pedido) {
        return service.atualizarDados(id, pedido.numFederacao(), pedido.estado());
    }

    @PostMapping("/{id}/responsaveis")
    public AlunoView adicionarResponsavel(@PathVariable UUID id,
                                         @Valid @RequestBody Pedidos.ResponsavelRequest pedido) {
        return service.adicionarResponsavel(id, pedido.pessoaId(), pedido.papeis().paraDominio());
    }

    @PutMapping("/{id}/responsaveis/{pessoaId}")
    public AlunoView alterarPapeis(@PathVariable UUID id, @PathVariable UUID pessoaId,
                                  @Valid @RequestBody Pedidos.PapeisRequest pedido) {
        return service.alterarPapeis(id, pessoaId, pedido.paraDominio());
    }

    @DeleteMapping("/{id}/responsaveis/{pessoaId}")
    public AlunoView terminarRelacao(@PathVariable UUID id, @PathVariable UUID pessoaId) {
        return service.terminarRelacao(id, pessoaId);
    }

    /** Quem é faturado pelas mensalidades do aluno. */
    @GetMapping("/{id}/responsavel-fiscal")
    public ResponsavelFiscalView responsavelFiscal(@PathVariable UUID id) {
        return service.responsavelFiscal(id);
    }

    /** Transfere a responsabilidade fiscal para outro responsável já associado ao aluno. */
    @PutMapping("/{id}/responsavel-fiscal")
    public AlunoView definirResponsavelFiscal(@PathVariable UUID id,
                                             @Valid @RequestBody Pedidos.ResponsavelFiscalRequest pedido) {
        return service.definirResponsavelFiscal(id, pedido.pessoaId());
    }
}
