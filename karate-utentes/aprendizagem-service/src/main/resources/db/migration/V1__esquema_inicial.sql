-- Réplica local dos alunos (alimentada pelo tópico karate.utentes.alunos).
-- Só guarda o necessário para validar e apresentar; o dono dos dados é o utentes-service.
CREATE TABLE aluno_ref (
    id               UUID PRIMARY KEY,
    nome             VARCHAR(200) NOT NULL,
    data_nascimento  DATE,
    data_inscricao   DATE,
    estado           VARCHAR(20)  NOT NULL,
    versao           BIGINT       NOT NULL,
    atualizado_em    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_aluno_ref_nome ON aluno_ref (lower(nome));

-- Catálogo de graduações. "ordem" define a progressão (maior = mais avançada).
CREATE TABLE graduacao (
    codigo             VARCHAR(20) PRIMARY KEY,
    nome               VARCHAR(50) NOT NULL,
    cor_faixa          VARCHAR(50) NOT NULL,
    tipo               VARCHAR(5)  NOT NULL,
    ordem              INT         NOT NULL,
    presencas_minimas  INT         NOT NULL DEFAULT 0,
    meses_minimos      INT         NOT NULL DEFAULT 0,
    ativa              BOOLEAN     NOT NULL DEFAULT true,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_graduacao_ordem UNIQUE (ordem),
    CONSTRAINT ck_graduacao_tipo CHECK (tipo IN ('KYU', 'DAN')),
    CONSTRAINT ck_graduacao_minimos CHECK (presencas_minimas >= 0 AND meses_minimos >= 0)
);

-- Temas (técnicas, katas, kumite, teoria). graduacao_codigo = programa de exame a que pertence.
CREATE TABLE tema (
    id                UUID PRIMARY KEY,
    nome              VARCHAR(200) NOT NULL,
    categoria         VARCHAR(20)  NOT NULL,
    descricao         TEXT,
    graduacao_codigo  VARCHAR(20) REFERENCES graduacao (codigo),
    ativo             BOOLEAN      NOT NULL DEFAULT true,
    version           BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_tema_categoria CHECK (categoria IN ('KIHON', 'KATA', 'KUMITE', 'TEORIA', 'OUTRO'))
);

CREATE UNIQUE INDEX uq_tema_nome ON tema (lower(nome));
CREATE INDEX ix_tema_graduacao ON tema (graduacao_codigo);

CREATE TABLE turma (
    id         UUID PRIMARY KEY,
    nome       VARCHAR(100) NOT NULL,
    descricao  VARCHAR(500),
    instrutor  VARCHAR(200),
    ativa      BOOLEAN NOT NULL DEFAULT true,
    version    BIGINT  NOT NULL DEFAULT 0,
    CONSTRAINT uq_turma_nome UNIQUE (nome)
);

CREATE TABLE turma_aluno (
    turma_id  UUID NOT NULL REFERENCES turma (id),
    aluno_id  UUID NOT NULL REFERENCES aluno_ref (id),
    PRIMARY KEY (turma_id, aluno_id)
);

CREATE INDEX ix_turma_aluno_aluno ON turma_aluno (aluno_id);

-- Aula (sessão de treino). turma_id nulo = aula avulsa (estágio, seminário).
CREATE TABLE aula (
    id               UUID PRIMARY KEY,
    turma_id         UUID REFERENCES turma (id),
    data             DATE    NOT NULL,
    hora_inicio      TIME    NOT NULL,
    duracao_minutos  INT     NOT NULL,
    instrutor        VARCHAR(200),
    sumario          TEXT,
    cancelada        BOOLEAN NOT NULL DEFAULT false,
    version          BIGINT  NOT NULL DEFAULT 0,
    CONSTRAINT ck_aula_duracao CHECK (duracao_minutos > 0)
);

CREATE INDEX ix_aula_data ON aula (data);
CREATE INDEX ix_aula_turma_data ON aula (turma_id, data);

-- Temas trabalhados em cada aula.
CREATE TABLE aula_tema (
    aula_id  UUID NOT NULL REFERENCES aula (id),
    tema_id  UUID NOT NULL REFERENCES tema (id),
    PRIMARY KEY (aula_id, tema_id)
);

CREATE INDEX ix_aula_tema_tema ON aula_tema (tema_id);

CREATE TABLE presenca (
    id            UUID PRIMARY KEY,
    aula_id       UUID        NOT NULL REFERENCES aula (id),
    aluno_id      UUID        NOT NULL REFERENCES aluno_ref (id),
    estado        VARCHAR(20) NOT NULL,
    observacao    VARCHAR(500),
    registado_em  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_presenca_aula_aluno UNIQUE (aula_id, aluno_id),
    CONSTRAINT ck_presenca_estado CHECK (estado IN ('PRESENTE', 'ATRASADO', 'FALTA', 'FALTA_JUSTIFICADA'))
);

CREATE INDEX ix_presenca_aluno ON presenca (aluno_id);

-- Avaliações do domínio de cada tema por aluno (histórico; a mais recente é a atual).
CREATE TABLE avaliacao_tema (
    id            UUID PRIMARY KEY,
    aluno_id      UUID        NOT NULL REFERENCES aluno_ref (id),
    tema_id       UUID        NOT NULL REFERENCES tema (id),
    nivel         VARCHAR(20) NOT NULL,
    data          DATE        NOT NULL,
    avaliador     VARCHAR(200),
    observacao    VARCHAR(500),
    registado_em  TIMESTAMPTZ NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_avaliacao_nivel CHECK (nivel IN ('INICIADO', 'EM_PROGRESSO', 'DOMINADO'))
);

CREATE INDEX ix_avaliacao_aluno_tema ON avaliacao_tema (aluno_id, tema_id, data DESC, registado_em DESC);

CREATE TABLE exame_graduacao (
    id                      UUID PRIMARY KEY,
    aluno_id                UUID         NOT NULL REFERENCES aluno_ref (id),
    graduacao_codigo        VARCHAR(20)  NOT NULL REFERENCES graduacao (codigo),
    data                    DATE         NOT NULL,
    examinador              VARCHAR(200) NOT NULL,
    resultado               VARCHAR(20)  NOT NULL,
    observacoes             VARCHAR(1000),
    requisitos_dispensados  BOOLEAN      NOT NULL,
    motivo_dispensa         VARCHAR(500),
    registado_em            TIMESTAMPTZ  NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_exame_resultado CHECK (resultado IN ('APROVADO', 'REPROVADO')),
    CONSTRAINT ck_exame_dispensa CHECK (NOT requisitos_dispensados OR motivo_dispensa IS NOT NULL)
);

CREATE INDEX ix_exame_aluno ON exame_graduacao (aluno_id, data);
-- Cada graduação só pode ser obtida uma vez por aluno.
CREATE UNIQUE INDEX uq_exame_aprovado ON exame_graduacao (aluno_id, graduacao_codigo)
    WHERE resultado = 'APROVADO';

-- Versão do snapshot de progresso de cada aluno (versionamento externo no read-side).
-- O incremento faz também de lock por aluno durante a construção do snapshot.
CREATE TABLE progresso_aluno (
    aluno_id  UUID PRIMARY KEY,
    versao    BIGINT NOT NULL
);

-- Outbox transacional (biblioteca karate-outbox).
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
