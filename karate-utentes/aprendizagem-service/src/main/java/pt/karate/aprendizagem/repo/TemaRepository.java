package pt.karate.aprendizagem.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pt.karate.aprendizagem.domain.CategoriaTema;
import pt.karate.aprendizagem.domain.Tema;

import java.util.List;
import java.util.UUID;

public interface TemaRepository extends JpaRepository<Tema, UUID> {

    @Query("""
            select t from Tema t left join t.graduacao g
            where (:categoria is null or t.categoria = :categoria)
              and (:graduacao is null or g.codigo = :graduacao)
              and (:incluirInativos = true or t.ativo = true)
            order by g.ordem nulls last, t.categoria, t.nome
            """)
    List<Tema> pesquisar(CategoriaTema categoria, String graduacao, boolean incluirInativos);

    @Query("select t from Tema t where t.graduacao.codigo = :graduacao and t.ativo = true order by t.categoria, t.nome")
    List<Tema> programa(String graduacao);

    @Query("select count(t) > 0 from Tema t where lower(t.nome) = lower(:nome) and t.id <> :excluirId")
    boolean nomeEmUso(String nome, UUID excluirId);
}
