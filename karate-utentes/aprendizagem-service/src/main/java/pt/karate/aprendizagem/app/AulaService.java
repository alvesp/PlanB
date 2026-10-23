package pt.karate.aprendizagem.app;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.karate.aprendizagem.app.Vistas.AulaView;
import pt.karate.aprendizagem.app.Vistas.FolhaPresencas;
import pt.karate.aprendizagem.app.Vistas.LinhaFolha;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Aulas, temas trabalhados e registo de presenças. */
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
    public AulaView criar(DadosAula d) {
        Turma turma = d.turmaId() == null ? null : turmas.carregar(d.turmaId());
        Aula aula = new Aula(UUID.randomUUID(), turma, d.data(), d.horaInicio(), d.duracaoMinutos(),
                d.instrutor(), d.sumario(), temas(d.temaIds()));
        return Vistas.de(aulas.save(aula));
    }

    @Transactional
    public AulaView atualizar(UUID id, DadosAula d) {
        Aula aula = carregar(id);
        aula.atualizar(d.data(), d.horaInicio(), d.duracaoMinutos(), d.instrutor(), d.sumario());
        Set<Tema> novos = temas(d.temaIds());
        boolean temasMudaram = !novos.equals(aula.getTemas());
        aula.definirTemas(novos);
        if (temasMudaram) {
            // A contagem de prática por tema dos presentes mudou.
            eventos.progressoAlterado(presentes(aula));
        }
        return Vistas.de(aula);
    }

    @Transactional
    public AulaView cancelar(UUID id) {
        Aula aula = carregar(id);
        aula.cancelar();
        return Vistas.de(aula);
    }

    @Transactional(readOnly = true)
    public AulaView obter(UUID id) {
        return Vistas.de(carregar(id));
    }

    @Transactional(readOnly = true)
    public List<AulaView> pesquisar(UUID turmaId, LocalDate de, LocalDate ate) {
        LocalDate inicio = de != null ? de : LocalDate.now(clock).minusMonths(1);
        LocalDate fim = ate != null ? ate : LocalDate.now(clock).plusMonths(1);
        if (fim.isBefore(inicio)) {
            throw new RegraNegocioException("INTERVALO_INVALIDO", "A data final é anterior à inicial.");
        }
        List<Aula> encontradas = turmaId == null ? aulas.entre(inicio, fim) : aulas.daTurmaEntre(turmaId, inicio, fim);
        return encontradas.stream().map(Vistas::de).toList();
    }

    /**
     * Folha de presenças: alunos da turma (registados ou não) mais quem foi registado sem
     * pertencer à turma (ex.: aluno de outra turma que veio treinar).
     */
    @Transactional(readOnly = true)
    public FolhaPresencas folha(UUID aulaId) {
        Aula aula = carregar(aulaId);
        Set<UUID> membros = aula.getTurma().map(Turma::getAlunos).orElse(Set.of());
        Map<UUID, Presenca> registadas = aula.getPresencas().stream()
                .collect(Collectors.toMap(Presenca::getAlunoId, Function.identity()));

        Set<UUID> todos = new LinkedHashSet<>(membros);
        todos.addAll(registadas.keySet());
        Map<UUID, AlunoRef> refs = alunos.findAllById(todos).stream()
                .collect(Collectors.toMap(AlunoRef::getId, Function.identity()));

        List<LinhaFolha> linhas = new ArrayList<>();
        for (UUID alunoId : todos) {
            AlunoRef ref = refs.get(alunoId);
            Presenca p = registadas.get(alunoId);
            linhas.add(new LinhaFolha(alunoId, ref == null ? null : ref.getNome(), membros.contains(alunoId),
                    p == null ? null : p.getEstado().name(), p == null ? null : p.getObservacao()));
        }
        linhas.sort(Comparator.comparing(LinhaFolha::nome, Comparator.nullsLast(Comparator.naturalOrder())));
        return new FolhaPresencas(aula.getId(), aula.getData(), aula.getHoraInicio(),
                aula.getTurma().map(Turma::getNome).orElse(null), aula.isCancelada(), linhas);
    }

    /** Regista ou corrige presenças em lote (tudo ou nada) e publica o progresso dos alunos afetados. */
    @Transactional
    public FolhaPresencas registarPresencas(UUID aulaId, List<RegistoPresenca> registos) {
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
        eventos.progressoAlterado(alterados);
        return folha(aulaId);
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
