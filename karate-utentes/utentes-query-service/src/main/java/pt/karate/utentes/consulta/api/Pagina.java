package pt.karate.utentes.consulta.api;

import java.util.List;

public record Pagina<T>(List<T> itens, long total, int pagina, int tamanho) {
}
