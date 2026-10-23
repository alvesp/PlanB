package pt.karate.aprendizagem.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.karate.aprendizagem.domain.Presenca;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PresencaRepository extends JpaRepository<Presenca, UUID> {

    /** Presenças efetivas (presente ou atrasado) em aulas não canceladas, depois de {@code depoisDe} (exclusivo) até {@code ate}. */
    @Query("""
            select count(p) from Presenca p join p.aula a
            where p.alunoId = :alunoId and a.cancelada = false
              and p.estado in (pt.karate.aprendizagem.domain.EstadoPresenca.PRESENTE,
                               pt.karate.aprendizagem.domain.EstadoPresenca.ATRASADO)
              and (:depoisDe is null or a.data > :depoisDe)
              and a.data <= :ate
            """)
    long contarPresencas(UUID alunoId, LocalDate depoisDe, LocalDate ate);

    @Query("""
            select max(a.data) from Presenca p join p.aula a
            where p.alunoId = :alunoId and a.cancelada = false
              and p.estado in (pt.karate.aprendizagem.domain.EstadoPresenca.PRESENTE,
                               pt.karate.aprendizagem.domain.EstadoPresenca.ATRASADO)
            """)
    LocalDate ultimaPresenca(UUID alunoId);

    @Query("""
            select new pt.karate.aprendizagem.repo.PraticaTema(t.id, count(p), max(a.data))
            from Presenca p join p.aula a join a.temas t
            where p.alunoId = :alunoId and a.cancelada = false
              and p.estado in (pt.karate.aprendizagem.domain.EstadoPresenca.PRESENTE,
                               pt.karate.aprendizagem.domain.EstadoPresenca.ATRASADO)
            group by t.id
            """)
    List<PraticaTema> praticaPorTema(UUID alunoId);
}
