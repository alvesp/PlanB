-- Pessoas: alunos e responsáveis partilham a mesma tabela (um pai também pode treinar).
CREATE TABLE pessoa (
    id               UUID PRIMARY KEY,
    nome             VARCHAR(200) NOT NULL,
    data_nascimento  DATE         NOT NULL,
    email            VARCHAR(254),
    telefone         VARCHAR(30),
    morada           VARCHAR(500),
    -- Dados fiscais (opcionais; obrigatórios para quem é responsável fiscal)
    nif              VARCHAR(9),
    nome_faturacao   VARCHAR(200),
    morada_fiscal    VARCHAR(500),
    versao_evento    BIGINT       NOT NULL DEFAULT 0,
    version          BIGINT       NOT NULL DEFAULT 0,
    criado_em        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_pessoa_nif UNIQUE (nif),
    CONSTRAINT ck_pessoa_dados_fiscais_completos CHECK (
        (nif IS NULL AND nome_faturacao IS NULL AND morada_fiscal IS NULL)
        OR (nif IS NOT NULL AND nome_faturacao IS NOT NULL AND morada_fiscal IS NOT NULL)
    )
);

CREATE INDEX ix_pessoa_nome ON pessoa (lower(nome));

CREATE TABLE aluno (
    id               UUID PRIMARY KEY,
    pessoa_id        UUID        NOT NULL REFERENCES pessoa (id),
    estado           VARCHAR(20) NOT NULL,
    num_federacao    VARCHAR(30),
    graduacao_atual  VARCHAR(20),
    data_inscricao   DATE        NOT NULL,
    versao_evento    BIGINT      NOT NULL DEFAULT 0,
    version          BIGINT      NOT NULL DEFAULT 0,
    criado_em        TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_aluno_pessoa UNIQUE (pessoa_id),
    CONSTRAINT uq_aluno_num_federacao UNIQUE (num_federacao),
    CONSTRAINT ck_aluno_estado CHECK (estado IN ('ATIVO', 'SUSPENSO', 'INATIVO'))
);

-- Relação aluno ↔ responsável, com histórico (valido_ate preenchido = relação terminada).
-- Qualquer alteração de papéis fecha a linha vigente e cria uma nova.
CREATE TABLE relacao_responsavel (
    id                   UUID PRIMARY KEY,
    aluno_id             UUID        NOT NULL REFERENCES aluno (id),
    responsavel_id       UUID        NOT NULL REFERENCES pessoa (id),
    parentesco           VARCHAR(20) NOT NULL,
    responsavel_legal    BOOLEAN     NOT NULL,
    responsavel_fiscal   BOOLEAN     NOT NULL,
    contacto_emergencia  BOOLEAN     NOT NULL,
    autorizado_recolha   BOOLEAN     NOT NULL,
    valido_desde         DATE        NOT NULL,
    valido_ate           DATE,
    CONSTRAINT ck_relacao_parentesco CHECK (
        parentesco IN ('PAI', 'MAE', 'TUTOR', 'AVO', 'IRMAO', 'OUTRO', 'PROPRIO')
    ),
    CONSTRAINT ck_relacao_datas CHECK (valido_ate IS NULL OR valido_ate >= valido_desde),
    -- No máximo um responsável fiscal vigente por aluno.
    -- Diferido para permitir fechar a relação antiga e criar a nova na mesma transação,
    -- independentemente da ordem em que o Hibernate executa INSERT/UPDATE.
    CONSTRAINT ex_um_responsavel_fiscal_vigente
        EXCLUDE USING btree (aluno_id WITH =)
        WHERE (responsavel_fiscal AND valido_ate IS NULL)
        DEFERRABLE INITIALLY DEFERRED,
    -- No máximo uma relação vigente por par aluno/responsável.
    CONSTRAINT ex_uma_relacao_vigente
        EXCLUDE USING btree (aluno_id WITH =, responsavel_id WITH =)
        WHERE (valido_ate IS NULL)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX ix_relacao_responsavel ON relacao_responsavel (responsavel_id) WHERE valido_ate IS NULL;
CREATE INDEX ix_relacao_aluno ON relacao_responsavel (aluno_id);

CREATE TABLE graduacao_registo (
    id          UUID PRIMARY KEY,
    aluno_id    UUID        NOT NULL REFERENCES aluno (id),
    graduacao   VARCHAR(20) NOT NULL,
    data_exame  DATE        NOT NULL,
    examinador  VARCHAR(200)
);

CREATE INDEX ix_graduacao_aluno ON graduacao_registo (aluno_id, data_exame);

-- Outbox transacional: os eventos são gravados na mesma transação que a alteração
-- e publicados no Kafka pelo OutboxRelay (entrega at-least-once).
CREATE TABLE outbox_evento (
    id                 UUID PRIMARY KEY,
    aggregate_type     VARCHAR(50)  NOT NULL,
    aggregate_id       UUID         NOT NULL,
    aggregate_version  BIGINT       NOT NULL,
    event_type         VARCHAR(100) NOT NULL,
    topic              VARCHAR(200) NOT NULL,
    payload            JSONB        NOT NULL,
    criado_em          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    publicado_em       TIMESTAMPTZ,
    tentativas         INT          NOT NULL DEFAULT 0,
    ultimo_erro        TEXT
);

CREATE INDEX ix_outbox_pendentes ON outbox_evento (criado_em) WHERE publicado_em IS NULL;
