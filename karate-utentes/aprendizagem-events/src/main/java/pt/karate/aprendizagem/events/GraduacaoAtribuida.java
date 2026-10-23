package pt.karate.aprendizagem.events;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Um aluno foi aprovado num exame de graduação (ou viu reconhecida uma graduação obtida
 * noutro clube, com {@code requisitosDispensados}). {@code exameId} identifica o facto e
 * permite aos consumidores ignorar entregas repetidas.
 */
public record GraduacaoAtribuida(
        UUID exameId,
        UUID alunoId,
        String graduacaoCodigo,
        String graduacaoNome,
        String corFaixa,
        int ordem,
        LocalDate data,
        String examinador,
        boolean requisitosDispensados
) {
}
