package pt.karate.aprendizagem.app;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.karate.aprendizagem.domain.AlunoRef;
import pt.karate.aprendizagem.domain.AvaliacaoTema;
import pt.karate.aprendizagem.domain.ExameGraduacao;
import pt.karate.aprendizagem.domain.Graduacao;
import pt.karate.aprendizagem.domain.NaoEncontradoException;
import pt.karate.aprendizagem.domain.NivelDominio;
import pt.karate.aprendizagem.domain.RequisitosGraduacao;
import pt.karate.aprendizagem.domain.RequisitosGraduacao.Elegibilidade;
import pt.karate.aprendizagem.domain.Tema;
import pt.karate.aprendizagem.repo.AlunoRefRepository;
import pt.karate.aprendizagem.repo.AvaliacaoTemaRepository;
import pt.karate.aprendizagem.repo.ExameRepository;
import pt.karate.aprendizagem.repo.GraduacaoRepository;
import pt.karate.aprendizagem.repo.PresencaRepository;
import pt.karate.aprendizagem.repo.TemaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Calcula o estado de aprendizagem de um aluno a partir dos registos (exames, presenças,
 * avaliações). Usado pela API e para construir o snapshot publicado no Kafka.
 */
@Component
public class ProgressoService {

    /** Estado calculado; {@code ate} é a data de referência dos cálculos. */
    public record Progresso(
            AlunoRef aluno,
            LocalDate ate,
            Optional<ExameGraduacao> atual,
            Optional<Graduacao> proxima,
            long totalPresencas,
            long presencasDesdeUltimaGraduacao,
            LocalDate ultimaPresenca,
            List<ExameGraduacao> exames,
            List<AvaliacaoTema> avaliacoesAtuais,
            List<Tema> programaProxima,
            Elegibilidade elegibilidade
    ) {
        public List<ExameGraduacao> aprovados() {
            return exames.stream().filter(ExameGraduacao::aprovado).toList();
        }
    }

    private final AlunoRefRepository alunos;
    private final ExameRepository exames;
    private final GraduacaoRepository graduacoes;
    private final PresencaRepository presencas;
    private final AvaliacaoTemaRepository avaliacoes;
    private final TemaRepository temas;

    public ProgressoService(AlunoRefRepository alunos, ExameRepository exames, GraduacaoRepository graduacoes,
                            PresencaRepository presencas, AvaliacaoTemaRepository avaliacoes, TemaRepository temas) {
        this.alunos = alunos;
        this.exames = exames;
        this.graduacoes = graduacoes;
        this.presencas = presencas;
        this.avaliacoes = avaliacoes;
        this.temas = temas;
    }

    @Transactional(readOnly = true)
    public Progresso calcular(UUID alunoId, LocalDate ate) {
        AlunoRef aluno = alunos.findById(alunoId).orElseThrow(() -> new NaoEncontradoException("Aluno", alunoId));

        List<ExameGraduacao> historico = exames.historico(alunoId);
        Optional<ExameGraduacao> atual = historico.stream()
                .filter(ExameGraduacao::aprovado)
                .filter(e -> !e.getData().isAfter(ate))
                .max((a, b) -> Integer.compare(a.getGraduacao().getOrdem(), b.getGraduacao().getOrdem()));

        int ordemAtual = atual.map(e -> e.getGraduacao().getOrdem()).orElse(0);
        Optional<Graduacao> proxima = graduacoes.findFirstByAtivaTrueAndOrdemGreaterThanOrderByOrdemAsc(ordemAtual);

        LocalDate dataAtual = atual.map(ExameGraduacao::getData).orElse(null);
        long total = presencas.contarPresencas(alunoId, null, ate);
        long desde = presencas.contarPresencas(alunoId, dataAtual, ate);

        List<AvaliacaoTema> avaliacoesAtuais = avaliacoes.atuais(alunoId);
        List<Tema> programa = proxima.map(g -> temas.programa(g.getCodigo())).orElse(List.of());
        Set<UUID> idsPrograma = programa.stream().map(Tema::getId).collect(Collectors.toSet());
        int dominados = (int) avaliacoesAtuais.stream()
                .filter(a -> a.getNivel() == NivelDominio.DOMINADO)
                .filter(a -> idsPrograma.contains(a.getTema().getId()))
                .count();

        // Sem graduação, o tempo conta desde a inscrição na escola.
        LocalDate referencia = dataAtual != null ? dataAtual : aluno.getDataInscricao();
        Elegibilidade elegibilidade = RequisitosGraduacao.avaliar(
                proxima.map(Graduacao::ref).orElse(null), referencia, desde, programa.size(), dominados, ate);

        return new Progresso(aluno, ate, atual, proxima, total, desde, presencas.ultimaPresenca(alunoId),
                historico, avaliacoesAtuais, programa, elegibilidade);
    }
}
