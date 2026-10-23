package pt.karate.aprendizagem.events;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Estado de uma aula, com os temas trabalhados e as presenças registadas. */
public record AulaSnapshot(
        UUID aulaId,
        long versao,
        UUID turmaId,
        String turma,
        LocalDate data,
        LocalTime horaInicio,
        int duracaoMinutos,
        String instrutor,
        String sumario,
        boolean cancelada,
        List<TemaResumo> temas,
        List<PresencaRegistada> presencas
) {

    public record TemaResumo(UUID temaId, String nome, String categoria) {
    }

    public record PresencaRegistada(UUID alunoId, String estado, String observacao) {
    }
}
