package pt.karate.aprendizagem.repo;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Incrementa e devolve a versão do snapshot de progresso de um aluno.
 * O UPDATE bloqueia a linha até ao fim da transação, serializando as alterações
 * concorrentes ao mesmo aluno: o snapshot construído a seguir já vê as anteriores.
 */
@Repository
public class ProgressoVersaoRepository {

    private final JdbcClient jdbc;

    public ProgressoVersaoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long proxima(UUID alunoId) {
        return jdbc.sql("""
                        insert into progresso_aluno (aluno_id, versao) values (:id, 1)
                        on conflict (aluno_id) do update set versao = progresso_aluno.versao + 1
                        returning versao
                        """)
                .param("id", alunoId)
                .query(Long.class)
                .single();
    }
}
