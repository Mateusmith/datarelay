CREATE TABLE conectores (
    id UUID PRIMARY KEY,
    nome VARCHAR(100) NOT NULL UNIQUE,
    papel VARCHAR(10) NOT NULL CHECK (papel IN ('ORIGEM', 'DESTINO')),
    url_jdbc VARCHAR(500) NOT NULL,
    usuario VARCHAR(100) NOT NULL,
    referencia_segredo VARCHAR(150) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL
);

CREATE TABLE planos_replicacao (
    id UUID PRIMARY KEY,
    nome VARCHAR(120) NOT NULL UNIQUE,
    conector_origem_id UUID NOT NULL REFERENCES conectores(id),
    modo_padrao VARCHAR(15) NOT NULL CHECK (modo_padrao IN ('COMPLETA', 'INCREMENTAL')),
    tamanho_lote INTEGER NOT NULL CHECK (tamanho_lote BETWEEN 1 AND 10000),
    expressao_cron VARCHAR(100),
    proxima_execucao_em TIMESTAMPTZ,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL
);

CREATE TABLE destinos_plano (
    plano_id UUID NOT NULL REFERENCES planos_replicacao(id) ON DELETE CASCADE,
    conector_id UUID NOT NULL REFERENCES conectores(id),
    PRIMARY KEY (plano_id, conector_id)
);

CREATE TABLE mapeamentos_tabela (
    id UUID PRIMARY KEY,
    plano_id UUID NOT NULL REFERENCES planos_replicacao(id) ON DELETE CASCADE,
    ordem_mapeamento INTEGER NOT NULL,
    esquema_origem VARCHAR(63) NOT NULL,
    tabela_origem VARCHAR(63) NOT NULL,
    esquema_destino VARCHAR(63) NOT NULL,
    tabela_destino VARCHAR(63) NOT NULL,
    coluna_chave VARCHAR(63) NOT NULL,
    coluna_incremental VARCHAR(63),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (plano_id, ordem_mapeamento)
);

CREATE TABLE colunas_mapeamento (
    mapeamento_id UUID NOT NULL REFERENCES mapeamentos_tabela(id) ON DELETE CASCADE,
    ordem_coluna INTEGER NOT NULL,
    nome_coluna VARCHAR(63) NOT NULL,
    PRIMARY KEY (mapeamento_id, ordem_coluna),
    UNIQUE (mapeamento_id, nome_coluna)
);

CREATE TABLE execucoes_replicacao (
    id UUID PRIMARY KEY,
    plano_id UUID NOT NULL REFERENCES planos_replicacao(id),
    chave_idempotencia VARCHAR(150) NOT NULL,
    tipo_disparo VARCHAR(15) NOT NULL CHECK (tipo_disparo IN ('MANUAL', 'AGENDADA')),
    modo VARCHAR(15) NOT NULL CHECK (modo IN ('COMPLETA', 'INCREMENTAL')),
    status VARCHAR(30) NOT NULL CHECK (
        status IN ('NA_FILA', 'EM_EXECUCAO', 'CONCLUIDA', 'PARCIALMENTE_CONCLUIDA', 'FALHOU')
    ),
    iniciado_em TIMESTAMPTZ,
    finalizado_em TIMESTAMPTZ,
    linhas_lidas BIGINT NOT NULL DEFAULT 0,
    linhas_escritas BIGINT NOT NULL DEFAULT 0,
    motivo_falha VARCHAR(2000),
    criado_em TIMESTAMPTZ NOT NULL,
    UNIQUE (plano_id, chave_idempotencia)
);

CREATE TABLE execucoes_destino (
    id UUID PRIMARY KEY,
    execucao_id UUID NOT NULL REFERENCES execucoes_replicacao(id) ON DELETE CASCADE,
    conector_destino_id UUID NOT NULL REFERENCES conectores(id),
    status VARCHAR(15) NOT NULL CHECK (status IN ('EM_EXECUCAO', 'CONCLUIDA', 'FALHOU')),
    iniciado_em TIMESTAMPTZ NOT NULL,
    finalizado_em TIMESTAMPTZ,
    linhas_lidas BIGINT NOT NULL DEFAULT 0,
    linhas_escritas BIGINT NOT NULL DEFAULT 0,
    motivo_falha VARCHAR(2000),
    UNIQUE (execucao_id, conector_destino_id)
);

CREATE TABLE pontos_controle_replicacao (
    plano_id UUID NOT NULL REFERENCES planos_replicacao(id) ON DELETE CASCADE,
    conector_destino_id UUID NOT NULL REFERENCES conectores(id),
    mapeamento_id UUID NOT NULL REFERENCES mapeamentos_tabela(id) ON DELETE CASCADE,
    ultimo_valor_incremental TIMESTAMPTZ,
    ultimo_valor_chave BIGINT,
    atualizado_em TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (plano_id, conector_destino_id, mapeamento_id)
);

CREATE INDEX idx_planos_replicacao_vencidos
    ON planos_replicacao (proxima_execucao_em)
    WHERE ativo = TRUE AND proxima_execucao_em IS NOT NULL;

CREATE INDEX idx_execucoes_replicacao_plano_criacao
    ON execucoes_replicacao (plano_id, criado_em DESC);

CREATE INDEX idx_execucoes_destino_execucao
    ON execucoes_destino (execucao_id);
