package pt.karate.aprendizagem.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.karate.aprendizagem.domain.Aula;

import java.util.List;
import java.util.UUID;

public interface AulaRepository extends JpaRepository<Aula, UUID> {

    /** Aulas onde o tema foi trabalhado (para republicar quando o tema muda de nome). */
    @Query("select distinct a from Aula a join a.temas t where t.id = :temaId")
    List<Aula> comTema(UUID temaId);
}
