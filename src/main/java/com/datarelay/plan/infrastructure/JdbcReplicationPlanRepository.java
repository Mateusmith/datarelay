package com.datarelay.plan.infrastructure;

import com.datarelay.plan.domain.ReplicationMode;
import com.datarelay.plan.domain.ReplicationPlan;
import com.datarelay.plan.domain.ReplicationPlanRepository;
import com.datarelay.plan.domain.TableMapping;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcReplicationPlanRepository implements ReplicationPlanRepository {

    private static final String SELECIONAR_PLANO = """
        SELECT id, nome, conector_origem_id, modo_padrao, tamanho_lote, expressao_cron,
               proxima_execucao_em, ativo, criado_em, atualizado_em
        FROM planos_replicacao
        """;

    private static final RowMapper<LinhaPlano> MAPEADOR_LINHA_PLANO =
        (conjuntoResultados, numeroLinha) -> new LinhaPlano(
        conjuntoResultados.getObject("id", UUID.class),
        conjuntoResultados.getString("nome"),
        conjuntoResultados.getObject("conector_origem_id", UUID.class),
        ReplicationMode.valueOf(conjuntoResultados.getString("modo_padrao")),
        conjuntoResultados.getInt("tamanho_lote"),
        conjuntoResultados.getString("expressao_cron"),
        conjuntoResultados.getTimestamp("proxima_execucao_em") == null
            ? null : conjuntoResultados.getTimestamp("proxima_execucao_em").toInstant(),
        conjuntoResultados.getBoolean("ativo"),
        conjuntoResultados.getTimestamp("criado_em").toInstant(),
        conjuntoResultados.getTimestamp("atualizado_em").toInstant());

    private final JdbcClient clienteJdbc;

    public JdbcReplicationPlanRepository(JdbcClient clienteJdbc) {
        this.clienteJdbc = clienteJdbc;
    }

    @Override
    public void salvar(ReplicationPlan plano) {
        clienteJdbc.sql("""
                INSERT INTO planos_replicacao
                    (id, nome, conector_origem_id, modo_padrao, tamanho_lote, expressao_cron,
                     proxima_execucao_em, ativo, criado_em, atualizado_em)
                VALUES
                    (:id, :nome, :origemId, :modo, :tamanhoLote, :cron,
                     :proximaExecucaoEm, :ativo, :criadoEm, :atualizadoEm)
                """)
            .param("id", plano.id())
            .param("nome", plano.nome())
            .param("origemId", plano.conectorOrigemId())
            .param("modo", plano.modoPadrao().name())
            .param("tamanhoLote", plano.tamanhoLote())
            .param("cron", plano.expressaoCron())
            .param("proximaExecucaoEm", dataHora(plano.proximaExecucaoEm()))
            .param("ativo", plano.ativo())
            .param("criadoEm", dataHora(plano.criadoEm()))
            .param("atualizadoEm", dataHora(plano.atualizadoEm()))
            .update();

        for (UUID destinoId : plano.idsConectoresDestino()) {
            clienteJdbc.sql("INSERT INTO destinos_plano (plano_id, conector_id) VALUES (:planoId, :conectorId)")
                .param("planoId", plano.id())
                .param("conectorId", destinoId)
                .update();
        }
        for (TableMapping mapeamento : plano.mapeamentos()) {
            salvarMapeamento(plano.id(), mapeamento);
        }
    }

    @Override
    @Transactional
    public void atualizar(ReplicationPlan plano) {
        clienteJdbc.sql("""
                UPDATE planos_replicacao
                SET nome = :nome, conector_origem_id = :origemId, modo_padrao = :modo,
                    tamanho_lote = :tamanhoLote, expressao_cron = :cron,
                    proxima_execucao_em = :proximaExecucaoEm, ativo = :ativo,
                    atualizado_em = :atualizadoEm
                WHERE id = :id
                """)
            .param("id", plano.id())
            .param("nome", plano.nome())
            .param("origemId", plano.conectorOrigemId())
            .param("modo", plano.modoPadrao().name())
            .param("tamanhoLote", plano.tamanhoLote())
            .param("cron", plano.expressaoCron())
            .param("proximaExecucaoEm", dataHora(plano.proximaExecucaoEm()))
            .param("ativo", plano.ativo())
            .param("atualizadoEm", dataHora(plano.atualizadoEm()))
            .update();

        clienteJdbc.sql("DELETE FROM destinos_plano WHERE plano_id = :planoId")
            .param("planoId", plano.id())
            .update();
        clienteJdbc.sql("DELETE FROM mapeamentos_tabela WHERE plano_id = :planoId")
            .param("planoId", plano.id())
            .update();
        for (UUID destinoId : plano.idsConectoresDestino()) {
            clienteJdbc.sql("INSERT INTO destinos_plano (plano_id, conector_id) VALUES (:planoId, :conectorId)")
                .param("planoId", plano.id())
                .param("conectorId", destinoId)
                .update();
        }
        for (TableMapping mapeamento : plano.mapeamentos()) {
            salvarMapeamento(plano.id(), mapeamento);
        }
    }

    @Override
    public Optional<ReplicationPlan> buscarPorId(UUID id) {
        return clienteJdbc.sql(SELECIONAR_PLANO + " WHERE id = :id")
            .param("id", id)
            .query(MAPEADOR_LINHA_PLANO)
            .optional()
            .map(this::montar);
    }

    @Override
    public Optional<ReplicationPlan> buscarPorNome(String nome) {
        return clienteJdbc.sql(SELECIONAR_PLANO + " WHERE LOWER(nome) = LOWER(:nome)")
            .param("nome", nome)
            .query(MAPEADOR_LINHA_PLANO)
            .optional()
            .map(this::montar);
    }

    @Override
    public List<ReplicationPlan> buscarTodos() {
        return clienteJdbc.sql(SELECIONAR_PLANO + " ORDER BY nome")
            .query(MAPEADOR_LINHA_PLANO)
            .list()
            .stream()
            .map(this::montar)
            .toList();
    }

    @Override
    public List<ReplicationPlan> buscarVencidos(Instant agora, int limite) {
        return clienteJdbc.sql(SELECIONAR_PLANO + """
                WHERE ativo = TRUE AND proxima_execucao_em IS NOT NULL AND proxima_execucao_em <= :agora
                ORDER BY proxima_execucao_em
                LIMIT :limite
                """)
            .param("agora", dataHora(agora))
            .param("limite", limite)
            .query(MAPEADOR_LINHA_PLANO)
            .list()
            .stream()
            .map(this::montar)
            .toList();
    }

    @Override
    public void atualizarProximaExecucao(UUID planoId, Instant proximaExecucaoEm, Instant atualizadoEm) {
        clienteJdbc.sql("""
                UPDATE planos_replicacao
                SET proxima_execucao_em = :proximaExecucaoEm, atualizado_em = :atualizadoEm
                WHERE id = :planoId
                """)
            .param("planoId", planoId)
            .param("proximaExecucaoEm", dataHora(proximaExecucaoEm))
            .param("atualizadoEm", dataHora(atualizadoEm))
            .update();
    }

    @Override
    public void atualizarAtivacao(UUID planoId, boolean ativo, Instant proximaExecucaoEm, Instant atualizadoEm) {
        clienteJdbc.sql("""
                UPDATE planos_replicacao
                SET ativo = :ativo, proxima_execucao_em = :proximaExecucaoEm,
                    atualizado_em = :atualizadoEm
                WHERE id = :planoId
                """)
            .param("planoId", planoId)
            .param("ativo", ativo)
            .param("proximaExecucaoEm", dataHora(proximaExecucaoEm))
            .param("atualizadoEm", dataHora(atualizadoEm))
            .update();
    }

    @Override
    public boolean existePlanoAtivoComConector(UUID conectorId) {
        return clienteJdbc.sql("""
                SELECT EXISTS (
                    SELECT 1
                    FROM planos_replicacao plano
                    LEFT JOIN destinos_plano destino ON destino.plano_id = plano.id
                    WHERE plano.ativo = TRUE
                      AND (plano.conector_origem_id = :conectorId OR destino.conector_id = :conectorId)
                )
                """)
            .param("conectorId", conectorId)
            .query(Boolean.class)
            .single();
    }

    private void salvarMapeamento(UUID planoId, TableMapping mapeamento) {
        clienteJdbc.sql("""
                INSERT INTO mapeamentos_tabela
                    (id, plano_id, ordem_mapeamento, esquema_origem, tabela_origem, esquema_destino,
                     tabela_destino, coluna_chave, coluna_incremental, ativo)
                VALUES
                    (:id, :planoId, :ordemMapeamento, :esquemaOrigem, :tabelaOrigem, :esquemaDestino,
                     :tabelaDestino, :colunaChave, :colunaIncremental, :ativo)
                """)
            .param("id", mapeamento.id())
            .param("planoId", planoId)
            .param("ordemMapeamento", mapeamento.ordem())
            .param("esquemaOrigem", mapeamento.esquemaOrigem())
            .param("tabelaOrigem", mapeamento.tabelaOrigem())
            .param("esquemaDestino", mapeamento.esquemaDestino())
            .param("tabelaDestino", mapeamento.tabelaDestino())
            .param("colunaChave", mapeamento.colunaChave())
            .param("colunaIncremental", mapeamento.colunaIncremental())
            .param("ativo", mapeamento.ativo())
            .update();

        for (int indice = 0; indice < mapeamento.colunas().size(); indice++) {
            clienteJdbc.sql("""
                    INSERT INTO colunas_mapeamento (mapeamento_id, ordem_coluna, nome_coluna)
                    VALUES (:mapeamentoId, :ordemColuna, :nomeColuna)
                    """)
                .param("mapeamentoId", mapeamento.id())
                .param("ordemColuna", indice)
                .param("nomeColuna", mapeamento.colunas().get(indice))
                .update();
        }
    }

    private ReplicationPlan montar(LinhaPlano linha) {
        List<UUID> destinos = clienteJdbc.sql("""
                SELECT conector_id FROM destinos_plano WHERE plano_id = :planoId ORDER BY conector_id
                """)
            .param("planoId", linha.id())
            .query((conjuntoResultados, numeroLinha) -> conjuntoResultados.getObject("conector_id", UUID.class))
            .list();
        List<TableMapping> mapeamentos = clienteJdbc.sql("""
                SELECT id, ordem_mapeamento, esquema_origem, tabela_origem, esquema_destino, tabela_destino,
                       coluna_chave, coluna_incremental, ativo
                FROM mapeamentos_tabela
                WHERE plano_id = :planoId
                ORDER BY ordem_mapeamento
                """)
            .param("planoId", linha.id())
            .query((conjuntoResultados, numeroLinha) -> {
                UUID mapeamentoId = conjuntoResultados.getObject("id", UUID.class);
                List<String> colunas = buscarColunas(mapeamentoId);
                return new TableMapping(
                    mapeamentoId,
                    conjuntoResultados.getInt("ordem_mapeamento"),
                    conjuntoResultados.getString("esquema_origem"),
                    conjuntoResultados.getString("tabela_origem"),
                    conjuntoResultados.getString("esquema_destino"),
                    conjuntoResultados.getString("tabela_destino"),
                    conjuntoResultados.getString("coluna_chave"),
                    conjuntoResultados.getString("coluna_incremental"),
                    colunas,
                    conjuntoResultados.getBoolean("ativo"));
            })
            .list();
        return new ReplicationPlan(
            linha.id(), linha.nome(), linha.conectorOrigemId(), destinos, linha.modo(), linha.tamanhoLote(),
            linha.expressaoCron(), linha.proximaExecucaoEm(), linha.ativo(), mapeamentos, linha.criadoEm(), linha.atualizadoEm());
    }

    private List<String> buscarColunas(UUID mapeamentoId) {
        return clienteJdbc.sql("""
                SELECT nome_coluna FROM colunas_mapeamento
                WHERE mapeamento_id = :mapeamentoId ORDER BY ordem_coluna
                """)
            .param("mapeamentoId", mapeamentoId)
            .query(String.class)
            .list();
    }

    private Timestamp dataHora(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }

    private record LinhaPlano(
        UUID id,
        String nome,
        UUID conectorOrigemId,
        ReplicationMode modo,
        int tamanhoLote,
        String expressaoCron,
        Instant proximaExecucaoEm,
        boolean ativo,
        Instant criadoEm,
        Instant atualizadoEm
    ) {
    }
}
