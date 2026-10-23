-- As graduações passam a ser atribuídas pelo aprendizagem-service (evento GraduacaoAtribuida).
-- exame_id identifica o exame de origem e garante que cada evento é aplicado uma só vez.
ALTER TABLE graduacao_registo ADD COLUMN exame_id UUID;
ALTER TABLE graduacao_registo ADD COLUMN graduacao_codigo VARCHAR(20);
ALTER TABLE graduacao_registo ADD COLUMN ordem INT NOT NULL DEFAULT 0;

CREATE UNIQUE INDEX uq_graduacao_registo_exame ON graduacao_registo (exame_id) WHERE exame_id IS NOT NULL;
