package pt.karate.aprendizagem.repo;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Incrementa e devolve a versão do próximo snapshot de um agregado (versionamento externo
 * no read-side). O UPDATE bloqueia a linha até ao fim da transação, por isso alterações
 * concorrentes ao mesmo agregado ficam em série e o snapshot construído a seguir já vê as anteriores.
 */
@Repository
public class VersoesRepository {

    private final JdbcClient jdbc;

    public VersoesRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long proxima(String tipo, Object id) {
        return jdbc.sql("""
                        insert into versao_agregado (tipo, id, versao) values (:tipo, :id, 1)
                        on conflict (tipo, id) do update set versao = versao_agregado.versao + 1
                        returning versao
                        """)
                .param("tipo", tipo)
                .param("id", id.toString())
                .query(Long.class)
                .single();
    }
}
