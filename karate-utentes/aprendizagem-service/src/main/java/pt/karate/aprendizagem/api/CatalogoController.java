package pt.karate.aprendizagem.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.karate.aprendizagem.app.CatalogoService;
import pt.karate.aprendizagem.app.Vistas.GraduacaoView;
import pt.karate.aprendizagem.app.Vistas.TemaView;
import pt.karate.aprendizagem.domain.CategoriaTema;

import java.util.List;
import java.util.UUID;

/** Catálogo: graduações e temas (programa de cada graduação). */
@RestController
@RequestMapping("/api")
public class CatalogoController {

    private final CatalogoService service;

    public CatalogoController(CatalogoService service) {
        this.service = service;
    }

    @GetMapping("/graduacoes")
    public List<GraduacaoView> graduacoes() {
        return service.graduacoes();
    }

    @PostMapping("/graduacoes")
    @ResponseStatus(HttpStatus.CREATED)
    public GraduacaoView criarGraduacao(@Valid @RequestBody Pedidos.GraduacaoRequest pedido) {
        return service.criarGraduacao(pedido.codigo(), pedido.dados());
    }

    /** O código do caminho prevalece sobre o do corpo. */
    @PutMapping("/graduacoes/{codigo}")
    public GraduacaoView atualizarGraduacao(@PathVariable String codigo,
                                            @Valid @RequestBody Pedidos.GraduacaoRequest pedido) {
        return service.atualizarGraduacao(codigo, pedido.dados());
    }

    /** Temas do programa de exame de uma graduação. */
    @GetMapping("/graduacoes/{codigo}/programa")
    public List<TemaView> programa(@PathVariable String codigo) {
        return service.programa(codigo);
    }

    @GetMapping("/temas")
    public List<TemaView> temas(@RequestParam(required = false) CategoriaTema categoria,
                                @RequestParam(required = false) String graduacao,
                                @RequestParam(defaultValue = "false") boolean incluirInativos) {
        return service.pesquisarTemas(categoria, graduacao, incluirInativos);
    }

    @GetMapping("/temas/{id}")
    public TemaView tema(@PathVariable UUID id) {
        return service.tema(id);
    }

    @PostMapping("/temas")
    @ResponseStatus(HttpStatus.CREATED)
    public TemaView criarTema(@Valid @RequestBody Pedidos.TemaRequest pedido) {
        return service.criarTema(pedido.dados());
    }

    @PutMapping("/temas/{id}")
    public TemaView atualizarTema(@PathVariable UUID id, @Valid @RequestBody Pedidos.TemaRequest pedido) {
        return service.atualizarTema(id, pedido.dados());
    }
}
