package pt.karate.aprendizagem.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import pt.karate.aprendizagem.domain.AlunoRef;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AlunoRefRepository extends JpaRepository<AlunoRef, UUID> {

    /**
     * Insere ou atualiza a réplica, ignorando versões iguais ou anteriores à guardada
     * (eventos repetidos ou fora de ordem). Devolve 1 se escreveu, 0 se ignorou.
     */
    @Modifying
    @Query(value = """
            insert into aluno_ref (id, nome, data_nascimento, data_inscricao, estado, versao, atualizado_em)
            values (:id, :nome, :dataNascimento, :dataInscricao, :estado, :versao, now())
            on conflict (id) do update set
                nome = excluded.nome,
                data_nascimento = excluded.data_nascimento,
                data_inscricao = excluded.data_inscricao,
                estado = excluded.estado,
                versao = excluded.versao,
                atualizado_em = now()
            where aluno_ref.versao < excluded.versao
            """, nativeQuery = true)
    int upsert(UUID id, String nome, LocalDate dataNascimento, LocalDate dataInscricao, String estado, long versao);

    @Query("select a.id from AlunoRef a")
    List<UUID> todosOsIds();
}
