package pt.karate.aprendizagem.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.karate.aprendizagem.domain.AlunoRef;
import pt.karate.aprendizagem.domain.Aula;
import pt.karate.aprendizagem.domain.EstadoPresenca;
import pt.karate.aprendizagem.domain.NaoEncontradoException;
import pt.karate.aprendizagem.domain.Presenca;
import pt.karate.aprendizagem.domain.RegraNegocioException;
import pt.karate.aprendizagem.domain.Tema;
import pt.karate.aprendizagem.domain.Turma;
import pt.karate.aprendizagem.repo.AlunoRefRepository;
import pt.karate.aprendizagem.repo.AulaRepository;
import pt.karate.aprendizagem.repo.TemaRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Comandos sobre aulas, temas trabalhados e presenças. */
@Service
public class AulaService {

    public record DadosAula(UUID turmaId, LocalDate data, LocalTime horaInicio, int duracaoMinutos,
                            String instrutor, String sumario, Set<UUID> temaIds) {
    }

    public record RegistoPresenca(UUID alunoId, EstadoPresenca estado, String observacao) {
    }

    private final AulaRepository aulas;
    private final TurmaService turmas;
    private final TemaRepository temas;
    private final AlunoRefRepository alunos;
    private final EventosAprendizagem eventos;
    private final Clock clock;

    public AulaService(AulaRepository aulas, TurmaService turmas, TemaRepository temas, AlunoRefRepository alunos,
                       EventosAprendizagem eventos, Clock clock) {
        this.aulas = aulas;
        this.turmas = turmas;
        this.temas = temas;
        this.alunos = alunos;
        this.eventos = eventos;
        this.clock = clock;
    }

    @Transactional
    public ResultadoComando criar(DadosAula d) {
        Turma turma = d.turmaId() == null ? null : turmas.carregar(d.turmaId());
        Aula aula = new Aula(UUID.randomUUID(), turma, d.data(), d.horaInicio(), d.duracaoMinutos(),
                d.instrutor(), d.sumario(), temas(d.temaIds()));
        aulas.save(aula);
        return publicar(aula);
    }

    /** Atualiza horário, sumário e temas (a turma não muda). */
    @Transactional
    public ResultadoComando atualizar(UUID id, DadosAula d) {
        Aula aula = carregar(id);
        aula.atualizar(d.data(), d.horaInicio(), d.duracaoMinutos(), d.instrutor(), d.sumario());
        Set<Tema> novos = temas(d.temaIds());
        boolean temasMudaram = !novos.equals(aula.getTemas());
        aula.definirTemas(novos);
        ResultadoComando resultado = publicar(aula);
        if (temasMudaram) {
            // A prática por tema dos alunos presentes mudou.
            eventos.progressoAlterado(presentes(aula));
        }
        return resultado;
    }

    @Transactional
    public ResultadoComando cancelar(UUID id) {
        Aula aula = carregar(id);
        aula.cancelar();
        return publicar(aula);
    }

    /**
     * Regista ou corrige presenças em lote (tudo ou nada). Publica a aula e o progresso
     * dos alunos cujo registo mudou.
     */
    @Transactional
    public ResultadoComando registarPresencas(UUID aulaId, List<RegistoPresenca> registos) {
        Aula aula = carregar(aulaId);
        Set<UUID> vistos = new HashSet<>();
        List<UUID> alterados = new ArrayList<>();
        for (RegistoPresenca r : registos) {
            if (!vistos.add(r.alunoId())) {
                throw new RegraNegocioException("ALUNO_REPETIDO", "O aluno " + r.alunoId() + " aparece mais de uma vez.");
            }
            AlunoRef aluno = alunos.findById(r.alunoId())
                    .orElseThrow(() -> new NaoEncontradoException("Aluno", r.alunoId()));
            if (aula.registarPresenca(aluno, r.estado(), r.observacao(), clock)) {
                alterados.add(aluno.getId());
            }
        }
        ResultadoComando resultado = publicar(aula);
        eventos.progressoAlterado(alterados);
        return resultado;
    }

    private ResultadoComando publicar(Aula aula) {
        return new ResultadoComando(aula.getId().toString(), eventos.aulaAlterada(aula));
    }

    private List<UUID> presentes(Aula aula) {
        return aula.getPresencas().stream()
                .filter(p -> p.getEstado().contaComoPresenca())
                .map(Presenca::getAlunoId)
                .toList();
    }

    private Set<Tema> temas(Set<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        List<Tema> encontrados = temas.findAllById(ids);
        if (encontrados.size() != ids.size()) {
            throw new RegraNegocioException("TEMA_INEXISTENTE", "Um ou mais temas indicados não existem.");
        }
        return new HashSet<>(encontrados);
    }

    private Aula carregar(UUID id) {
        return aulas.findById(id).orElseThrow(() -> new NaoEncontradoException("Aula", id));
    }
}
