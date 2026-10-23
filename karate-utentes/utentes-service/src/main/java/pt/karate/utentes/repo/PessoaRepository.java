package pt.karate.utentes.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.karate.utentes.domain.Pessoa;

import java.util.UUID;

public interface PessoaRepository extends JpaRepository<Pessoa, UUID> {

    @Query("select count(p) > 0 from Pessoa p where p.dadosFiscais.nif = :nif and p.id <> :excluirId")
    boolean nifEmUsoPorOutra(String nif, UUID excluirId);
}
