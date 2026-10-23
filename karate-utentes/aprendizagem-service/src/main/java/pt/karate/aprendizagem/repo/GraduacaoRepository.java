package pt.karate.aprendizagem.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import pt.karate.aprendizagem.domain.Graduacao;

import java.util.Optional;

public interface GraduacaoRepository extends JpaRepository<Graduacao, String> {

    /** Graduação ativa imediatamente acima da ordem indicada (0 = primeira). */
    Optional<Graduacao> findFirstByAtivaTrueAndOrdemGreaterThanOrderByOrdemAsc(int ordem);

    boolean existsByOrdemAndCodigoNot(int ordem, String codigo);
}
