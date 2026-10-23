package pt.karate.aprendizagem.events;

import java.util.List;
import java.util.UUID;

/**
 * Estado de uma turma. Os alunos vão só por id: o read-side junta os nomes a partir do
 * índice de alunos, para não ficarem desatualizados quando um aluno muda de nome.
 */
public record TurmaSnapshot(
        UUID turmaId,
        long versao,
        String nome,
        String descricao,
        String instrutor,
        boolean ativa,
        List<UUID> alunos
) {
}
