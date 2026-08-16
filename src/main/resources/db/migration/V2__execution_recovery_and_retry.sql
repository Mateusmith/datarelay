ALTER TABLE execucoes_replicacao
    ADD COLUMN execucao_origem_id UUID REFERENCES execucoes_replicacao(id),
    ADD COLUMN conector_destino_restrito_id UUID REFERENCES conectores(id);

ALTER TABLE execucoes_replicacao
    DROP CONSTRAINT execucoes_replicacao_tipo_disparo_check;

ALTER TABLE execucoes_replicacao
    ADD CONSTRAINT execucoes_replicacao_tipo_disparo_check
    CHECK (tipo_disparo IN ('MANUAL', 'AGENDADA', 'REPROCESSAMENTO'));

ALTER TABLE execucoes_replicacao
    DROP CONSTRAINT execucoes_replicacao_status_check;

ALTER TABLE execucoes_replicacao
    ADD CONSTRAINT execucoes_replicacao_status_check
    CHECK (status IN ('NA_FILA', 'EM_EXECUCAO', 'CONCLUIDA',
                      'PARCIALMENTE_CONCLUIDA', 'FALHOU', 'CANCELADA'));

CREATE INDEX idx_execucoes_replicacao_status
    ON execucoes_replicacao (status, criado_em)
    WHERE status IN ('NA_FILA', 'EM_EXECUCAO');

CREATE INDEX idx_execucoes_replicacao_origem
    ON execucoes_replicacao (execucao_origem_id)
    WHERE execucao_origem_id IS NOT NULL;
