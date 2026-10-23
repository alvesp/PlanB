package pt.karate.aprendizagem.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.karate.aprendizagem.domain.AvaliacaoTema;

import java.util.List;
import java.util.UUID;

public interface AvaliacaoTemaRepository extends JpaRepository<AvaliacaoTema, UUID> {

    /** Avaliação mais recente de cada tema do aluno. */
    @Query("""
            select a from AvaliacaoTema a join fetch a.tema t
            where a.alunoId = :alunoId
              and not exists (
                  select 1 from AvaliacaoTema b
                  where b.alunoId = a.alunoId and b.tema = a.tema
                    and (b.data > a.data or (b.data = a.data and b.registadoEm > a.registadoEm)))
            order by t.nome
            """)
    List<AvaliacaoTema> atuais(UUID alunoId);

    @Query("select a from AvaliacaoTema a join fetch a.tema where a.alunoId = :alunoId order by a.data desc, a.registadoEm desc")
    List<AvaliacaoTema> historico(UUID alunoId);
}
