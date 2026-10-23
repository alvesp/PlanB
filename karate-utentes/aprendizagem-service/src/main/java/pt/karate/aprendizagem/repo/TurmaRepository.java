package pt.karate.aprendizagem.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.karate.aprendizagem.domain.Turma;

import java.util.UUID;

public interface TurmaRepository extends JpaRepository<Turma, UUID> {
}
