package pt.karate.aprendizagem.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Sessão de treino: quando foi, que temas foram trabalhados e quem esteve presente.
 */
@Entity
@Table(name = "aula")
public class Aula {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turma_id")
    private Turma turma;

    private LocalDate data;
    private LocalTime horaInicio;
    private int duracaoMinutos;
    private String instrutor;
    private String sumario;
    private boolean cancelada;

    @ManyToMany
    @JoinTable(name = "aula_tema",
            joinColumns = @JoinColumn(name = "aula_id"),
            inverseJoinColumns = @JoinColumn(name = "tema_id"))
    private Set<Tema> temas = new HashSet<>();

    @OneToMany(mappedBy = "aula", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Presenca> presencas = new ArrayList<>();

    @Version
    private Long version;

    protected Aula() {
    }

    public Aula(UUID id, Turma turma, LocalDate data, LocalTime horaInicio, int duracaoMinutos,
                String instrutor, String sumario, Collection<Tema> temas) {
        this.id = Objects.requireNonNull(id);
        if (turma != null && !turma.isAtiva()) {
            throw new RegraNegocioException("TURMA_INATIVA", "Não é possível criar aulas numa turma inativa.");
        }
        this.turma = turma;
        atualizar(data, horaInicio, duracaoMinutos, instrutor == null && turma != null ? turma.getInstrutor() : instrutor,
                sumario);
        definirTemas(temas);
    }

    public void atualizar(LocalDate data, LocalTime horaInicio, int duracaoMinutos, String instrutor, String sumario) {
        if (data == null || horaInicio == null) {
            throw new RegraNegocioException("AULA_SEM_DATA", "A data e a hora de início são obrigatórias.");
        }
        if (duracaoMinutos <= 0 || duracaoMinutos > 600) {
            throw new RegraNegocioException("DURACAO_INVALIDA", "A duração tem de estar entre 1 e 600 minutos.");
        }
        if (!presencas.isEmpty() && !data.equals(this.data)) {
            throw new RegraNegocioException("AULA_COM_PRESENCAS",
                    "Não é possível mudar a data de uma aula que já tem presenças registadas.");
        }
        this.data = data;
        this.horaInicio = horaInicio;
        this.duracaoMinutos = duracaoMinutos;
        this.instrutor = instrutor;
        this.sumario = sumario;
    }

    public void definirTemas(Collection<Tema> novos) {
        for (Tema t : novos) {
            if (!t.isAtivo()) {
                throw new RegraNegocioException("TEMA_INATIVO", "O tema " + t.getNome() + " está inativo.");
            }
        }
        temas.clear();
        temas.addAll(novos);
    }

    public void cancelar() {
        if (presencas.stream().anyMatch(p -> p.getEstado().contaComoPresenca())) {
            throw new RegraNegocioException("AULA_COM_PRESENCAS",
                    "Não é possível cancelar uma aula com presenças registadas.");
        }
        presencas.clear();
        cancelada = true;
    }

    /**
     * Regista (ou corrige) a presença de um aluno. Permitido para aulas de hoje ou passadas.
     *
     * @return {@code true} se o registo mudou alguma coisa
     */
    public boolean registarPresenca(AlunoRef aluno, EstadoPresenca estado, String observacao, Clock clock) {
        Objects.requireNonNull(estado, "estado");
        if (cancelada) {
            throw new RegraNegocioException("AULA_CANCELADA", "A aula foi cancelada.");
        }
        if (data.isAfter(LocalDate.now(clock))) {
            throw new RegraNegocioException("AULA_FUTURA", "Não é possível registar presenças numa aula futura.");
        }
        aluno.garantirAtivo();

        Optional<Presenca> existente = presenca(aluno.getId());
        if (existente.isPresent()) {
            return existente.get().alterar(estado, observacao, clock);
        }
        presencas.add(new Presenca(this, aluno.getId(), estado, observacao, clock));
        return true;
    }

    public Optional<Presenca> presenca(UUID alunoId) {
        return presencas.stream().filter(p -> p.getAlunoId().equals(alunoId)).findFirst();
    }

    public UUID getId() {
        return id;
    }

    public Optional<Turma> getTurma() {
        return Optional.ofNullable(turma);
    }

    public LocalDate getData() {
        return data;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public int getDuracaoMinutos() {
        return duracaoMinutos;
    }

    public String getInstrutor() {
        return instrutor;
    }

    public String getSumario() {
        return sumario;
    }

    public boolean isCancelada() {
        return cancelada;
    }

    public Set<Tema> getTemas() {
        return Set.copyOf(temas);
    }

    public List<Presenca> getPresencas() {
        return List.copyOf(presencas);
    }
}
