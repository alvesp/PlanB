package pt.karate.utentes.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.karate.utentes.domain.Aluno;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlunoRepository extends JpaRepository<Aluno, UUID> {

    Optional<Aluno> findByPessoaId(UUID pessoaId);

    boolean existsByPessoaId(UUID pessoaId);

    /** Alunos pelos quais a pessoa é responsável neste momento. */
    @Query("""
            select distinct a from Aluno a
            join a.relacoes r
            where r.responsavel.id = :pessoaId and r.validoAte is null
            """)
    List<Aluno> findComResponsavelVigente(UUID pessoaId);
}
