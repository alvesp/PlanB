package pt.karate.aprendizagem.repo;

import pt.karate.aprendizagem.domain.EstadoPresenca;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Linha do registo de presenças de um aluno, com os dados da aula. */
public record RegistoPresenca(UUID aulaId, LocalDate data, LocalTime horaInicio, String turma,
                              EstadoPresenca estado, String observacao) {
}
