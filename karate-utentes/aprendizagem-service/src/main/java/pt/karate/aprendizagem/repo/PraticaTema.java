package pt.karate.aprendizagem.repo;

import java.time.LocalDate;
import java.util.UUID;

/** Quantas vezes o aluno esteve presente em aulas onde o tema foi trabalhado. */
public record PraticaTema(UUID temaId, long vezes, LocalDate ultimaVez) {
}
