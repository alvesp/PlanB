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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pt.karate.utentes.app.PessoaService;
import pt.karate.utentes.app.Vistas.PessoaView;

import java.util.UUID;

/** Pessoas: alunos e responsáveis (pais, mães, tutores...). */
@RestController
@RequestMapping("/api/pessoas")
public class PessoaController {

    private final PessoaService service;

    public PessoaController(PessoaService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<PessoaView> registar(@Valid @RequestBody Pedidos.PessoaRequest pedido) {
        PessoaView criada = service.registar(pedido.dados(), pedido.fiscais());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(location).body(criada);
    }

    @GetMapping("/{id}")
    public PessoaView obter(@PathVariable UUID id) {
        return service.obter(id);
    }

    /** Atualiza dados pessoais e contactos (os dados fiscais têm endpoint próprio). */
    @PutMapping("/{id}")
    public PessoaView atualizar(@PathVariable UUID id, @Valid @RequestBody Pedidos.DadosPessoaRequest pedido) {
        return service.atualizar(id, pedido.dados());
    }

    @PutMapping("/{id}/dados-fiscais")
    public PessoaView definirDadosFiscais(@PathVariable UUID id,
                                          @Valid @RequestBody Pedidos.DadosFiscaisRequest pedido) {
        return service.definirDadosFiscais(id, pedido.paraDominio());
    }

    @DeleteMapping("/{id}/dados-fiscais")
    public PessoaView removerDadosFiscais(@PathVariable UUID id) {
        return service.definirDadosFiscais(id, null);
    }
}
