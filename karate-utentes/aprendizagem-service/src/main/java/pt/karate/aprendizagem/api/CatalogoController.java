package pt.karate.aprendizagem.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.karate.aprendizagem.app.CatalogoService;
import pt.karate.aprendizagem.app.ResultadoComando;

import java.util.UUID;

/**
 * Comandos do catálogo (graduações e temas). Consultas em {@code /api/consulta/graduacoes}
 * e {@code /api/consulta/temas} no utentes-query-service.
 */
@RestController
@RequestMapping("/api")
public class CatalogoController {

    private final CatalogoService service;

    public CatalogoController(CatalogoService service) {
        this.service = service;
    }

    @PostMapping("/graduacoes")
    @ResponseStatus(HttpStatus.CREATED)
    public ResultadoComando criarGraduacao(@Valid @RequestBody Pedidos.GraduacaoRequest pedido) {
        return service.criarGraduacao(pedido.codigo(), pedido.dados());
    }

    /** O código do caminho prevalece sobre o do corpo. */
    @PutMapping("/graduacoes/{codigo}")
    public ResultadoComando atualizarGraduacao(@PathVariable String codigo,
                                               @Valid @RequestBody Pedidos.GraduacaoRequest pedido) {
        return service.atualizarGraduacao(codigo, pedido.dados());
    }

    @PostMapping("/temas")
    @ResponseStatus(HttpStatus.CREATED)
    public ResultadoComando criarTema(@Valid @RequestBody Pedidos.TemaRequest pedido) {
        return service.criarTema(pedido.dados());
    }

    @PutMapping("/temas/{id}")
    public ResultadoComando atualizarTema(@PathVariable UUID id, @Valid @RequestBody Pedidos.TemaRequest pedido) {
        return service.atualizarTema(id, pedido.dados());
    }
}
