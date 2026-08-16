package com.datarelay.execution.infrastructure;

import com.datarelay.execution.domain.CheckpointRepository;
import com.datarelay.execution.domain.ReplicationCheckpoint;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCheckpointRepository implements CheckpointRepository {

    private final JdbcClient clienteJdbc;

    public JdbcCheckpointRepository(JdbcClient clienteJdbc) {
        this.clienteJdbc = clienteJdbc;
    }

    @Override
    public Optional<ReplicationCheckpoint> buscar(UUID planoId, UUID conectorDestinoId, UUID mapeamentoId) {
        return clienteJdbc.sql("""
                SELECT plano_id, conector_destino_id, mapeamento_id, ultimo_valor_incremental,
                       ultimo_valor_chave, atualizado_em
                FROM pontos_controle_replicacao
                WHERE plano_id = :planoId
                  AND conector_destino_id = :destinoId
                  AND mapeamento_id = :mapeamentoId
                """)
            .param("planoId", planoId)
            .param("destinoId", conectorDestinoId)
            .param("mapeamentoId", mapeamentoId)
            .query((conjuntoResultados, numeroLinha) -> new ReplicationCheckpoint(
                conjuntoResultados.getObject("plano_id", UUID.class),
                conjuntoResultados.getObject("conector_destino_id", UUID.class),
                conjuntoResultados.getObject("mapeamento_id", UUID.class),
                conjuntoResultados.getTimestamp("ultimo_valor_incremental") == null
                    ? null : conjuntoResultados.getTimestamp("ultimo_valor_incremental").toInstant(),
                conjuntoResultados.getObject("ultimo_valor_chave") == null ? null : conjuntoResultados.getLong("ultimo_valor_chave"),
                conjuntoResultados.getTimestamp("atualizado_em").toInstant()))
            .optional();
    }

    @Override
    public void salvar(ReplicationCheckpoint pontoControle) {
        clienteJdbc.sql("""
                INSERT INTO pontos_controle_replicacao
                    (plano_id, conector_destino_id, mapeamento_id, ultimo_valor_incremental,
                     ultimo_valor_chave, atualizado_em)
                VALUES
                    (:planoId, :destinoId, :mapeamentoId, :ultimoValorIncremental,
                     :ultimoValorChave, :atualizadoEm)
                ON CONFLICT (plano_id, conector_destino_id, mapeamento_id)
                DO UPDATE SET
                    ultimo_valor_incremental = EXCLUDED.ultimo_valor_incremental,
                    ultimo_valor_chave = EXCLUDED.ultimo_valor_chave,
                    atualizado_em = EXCLUDED.atualizado_em
                WHERE
                    (pontos_controle_replicacao.ultimo_valor_incremental IS NULL
                        AND EXCLUDED.ultimo_valor_incremental IS NOT NULL)
                    OR (pontos_controle_replicacao.ultimo_valor_incremental IS NULL
                        AND EXCLUDED.ultimo_valor_incremental IS NULL
                        AND EXCLUDED.ultimo_valor_chave >= pontos_controle_replicacao.ultimo_valor_chave)
                    OR (pontos_controle_replicacao.ultimo_valor_incremental IS NOT NULL
                        AND EXCLUDED.ultimo_valor_incremental IS NOT NULL
                        AND (EXCLUDED.ultimo_valor_incremental, EXCLUDED.ultimo_valor_chave)
                            >= (pontos_controle_replicacao.ultimo_valor_incremental,
                                pontos_controle_replicacao.ultimo_valor_chave))
                """)
            .param("planoId", pontoControle.planoId())
            .param("destinoId", pontoControle.conectorDestinoId())
            .param("mapeamentoId", pontoControle.mapeamentoId())
            .param("ultimoValorIncremental", pontoControle.ultimoValorIncremental() == null
                ? null : Timestamp.from(pontoControle.ultimoValorIncremental()))
            .param("ultimoValorChave", pontoControle.ultimoValorChave())
            .param("atualizadoEm", Timestamp.from(pontoControle.atualizadoEm()))
            .update();
    }
}
