package pt.karate.aprendizagem.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.karate.aprendizagem.domain.ExameGraduacao;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExameRepository extends JpaRepository<ExameGraduacao, UUID> {

    @Query("select e from ExameGraduacao e join fetch e.graduacao where e.alunoId = :alunoId order by e.data, e.registadoEm")
    List<ExameGraduacao> historico(UUID alunoId);

    /** Exame aprovado mais recente (graduação atual). */
    @Query("""
            select e from ExameGraduacao e join fetch e.graduacao g
            where e.alunoId = :alunoId and e.resultado = pt.karate.aprendizagem.domain.ResultadoExame.APROVADO
            order by g.ordem desc
            limit 1
            """)
    Optional<ExameGraduacao> graduacaoAtual(UUID alunoId);
}
