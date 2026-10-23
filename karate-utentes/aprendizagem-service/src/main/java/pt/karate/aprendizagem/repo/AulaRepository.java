package pt.karate.aprendizagem.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.karate.aprendizagem.domain.Aula;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AulaRepository extends JpaRepository<Aula, UUID> {

    @Query("""
            select a from Aula a
            where a.data between :de and :ate
            order by a.data, a.horaInicio
            """)
    List<Aula> entre(LocalDate de, LocalDate ate);

    // Consulta separada em vez de ":turmaId is null": o PostgreSQL não infere o tipo de um UUID nulo.
    @Query("""
            select a from Aula a
            where a.turma.id = :turmaId and a.data between :de and :ate
            order by a.data, a.horaInicio
            """)
    List<Aula> daTurmaEntre(UUID turmaId, LocalDate de, LocalDate ate);
}
