package pt.karate.utentes.api;

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
import pt.karate.utentes.app.AlunoService;
import pt.karate.utentes.app.ResultadoComando;

import java.util.UUID;

/**
 * Comandos sobre alunos. As leituras (ficha, responsáveis, responsável fiscal, histórico)
 * estão no read-side: {@code /api/consulta/alunos/...} no utentes-query-service.
 */
@RestController
@RequestMapping("/api/alunos")
public class AlunoController {

    private final AlunoService service;

    public AlunoController(AlunoService service) {
        this.service = service;
    }

    /** Inscreve um aluno já com os responsáveis (exatamente um tem de ser responsável fiscal). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResultadoComando inscrever(@Valid @RequestBody Pedidos.InscricaoRequest pedido) {
        return service.inscrever(pedido.paraComando());
    }

    @PutMapping("/{id}")
    public ResultadoComando atualizarDados(@PathVariable UUID id, @Valid @RequestBody Pedidos.DadosAlunoRequest pedido) {
        return service.atualizarDados(id, pedido.numFederacao(), pedido.estado());
    }

    @PostMapping("/{id}/responsaveis")
    public ResultadoComando adicionarResponsavel(@PathVariable UUID id,
                                                 @Valid @RequestBody Pedidos.ResponsavelRequest pedido) {
        return service.adicionarResponsavel(id, pedido.pessoaId(), pedido.papeis().paraDominio());
    }

    @PutMapping("/{id}/responsaveis/{pessoaId}")
    public ResultadoComando alterarPapeis(@PathVariable UUID id, @PathVariable UUID pessoaId,
                                          @Valid @RequestBody Pedidos.PapeisRequest pedido) {
        return service.alterarPapeis(id, pessoaId, pedido.paraDominio());
    }

    @DeleteMapping("/{id}/responsaveis/{pessoaId}")
    public ResultadoComando terminarRelacao(@PathVariable UUID id, @PathVariable UUID pessoaId) {
        return service.terminarRelacao(id, pessoaId);
    }

    /** Transfere a responsabilidade fiscal para outro responsável já associado ao aluno. */
    @PutMapping("/{id}/responsavel-fiscal")
    public ResultadoComando definirResponsavelFiscal(@PathVariable UUID id,
                                                     @Valid @RequestBody Pedidos.ResponsavelFiscalRequest pedido) {
        return service.definirResponsavelFiscal(id, pedido.pessoaId());
    }
}
