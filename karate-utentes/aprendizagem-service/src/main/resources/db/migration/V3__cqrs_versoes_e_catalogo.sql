-- CQRS: todos os agregados publicam snapshots para o read-side, cada um com a sua versão.
-- Tabela genérica de versões (substitui progresso_aluno). O incremento bloqueia a linha até
-- ao fim da transação, serializando alterações concorrentes ao mesmo agregado.
CREATE TABLE versao_agregado (
    tipo    VARCHAR(50)  NOT NULL,
    id      VARCHAR(100) NOT NULL,
    versao  BIGINT       NOT NULL,
    PRIMARY KEY (tipo, id)
);

INSERT INTO versao_agregado (tipo, id, versao)
SELECT 'ProgressoAluno', aluno_id::text, versao FROM progresso_aluno;

DROP TABLE progresso_aluno;

-- Publica o catálogo de graduações já existente (inserido pela V2) para o read-side o conhecer.
-- A chave é md5('graduacao:' || codigo)::uuid, igual a TopicosAprendizagem.chaveGraduacao().
INSERT INTO versao_agregado (tipo, id, versao)
SELECT 'Graduacao', codigo, 1 FROM graduacao;

INSERT INTO outbox_evento (id, aggregate_type, aggregate_id, aggregate_version, event_type, topic, payload)
SELECT gen_random_uuid(),
       'Graduacao',
       md5('graduacao:' || codigo)::uuid,
       1,
       'GraduacaoCatalogoAtualizada',
       'karate.aprendizagem.catalogo.graduacoes',
       jsonb_build_object(
           'codigo', codigo,
           'versao', 1,
           'nome', nome,
           'corFaixa', cor_faixa,
           'tipo', tipo,
           'ordem', ordem,
           'presencasMinimas', presencas_minimas,
           'mesesMinimos', meses_minimos,
           'ativa', ativa)
FROM graduacao;
