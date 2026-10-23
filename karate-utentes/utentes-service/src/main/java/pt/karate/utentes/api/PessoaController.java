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
import pt.karate.utentes.app.PessoaService;
import pt.karate.utentes.app.ResultadoComando;

import java.util.UUID;

/**
 * Comandos sobre pessoas (alunos e responsáveis). As leituras estão no read-side:
 * {@code GET /api/consulta/pessoas/{id}} no utentes-query-service.
 */
@RestController
@RequestMapping("/api/pessoas")
public class PessoaController {

    private final PessoaService service;

    public PessoaController(PessoaService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResultadoComando registar(@Valid @RequestBody Pedidos.PessoaRequest pedido) {
        return service.registar(pedido.dados(), pedido.fiscais());
    }

    /** Atualiza dados pessoais e contactos (os dados fiscais têm comando próprio). */
    @PutMapping("/{id}")
    public ResultadoComando atualizar(@PathVariable UUID id, @Valid @RequestBody Pedidos.DadosPessoaRequest pedido) {
        return service.atualizar(id, pedido.dados());
    }

    @PutMapping("/{id}/dados-fiscais")
    public ResultadoComando definirDadosFiscais(@PathVariable UUID id,
                                                @Valid @RequestBody Pedidos.DadosFiscaisRequest pedido) {
        return service.definirDadosFiscais(id, pedido.paraDominio());
    }

    @DeleteMapping("/{id}/dados-fiscais")
    public ResultadoComando removerDadosFiscais(@PathVariable UUID id) {
        return service.definirDadosFiscais(id, null);
    }
}
