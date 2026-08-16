CREATE TABLE clientes (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    email VARCHAR(180) NOT NULL UNIQUE,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE pedidos (
    id BIGSERIAL PRIMARY KEY,
    cliente_id BIGINT NOT NULL REFERENCES clientes(id),
    total NUMERIC(12, 2) NOT NULL CHECK (total >= 0),
    status VARCHAR(30) NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO clientes (nome, email, atualizado_em) VALUES
    ('Ana Souza', 'ana@example.com', '2026-01-01T10:00:00Z'),
    ('Bruno Lima', 'bruno@example.com', '2026-01-01T10:01:00Z');

INSERT INTO pedidos (cliente_id, total, status, atualizado_em) VALUES
    (1, 249.90, 'PAGO', '2026-01-01T10:02:00Z'),
    (2, 89.50, 'PENDENTE', '2026-01-01T10:03:00Z');
