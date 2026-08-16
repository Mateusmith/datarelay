package com.datarelay.execution.infrastructure;

import com.datarelay.execution.domain.CreatedRun;
import com.datarelay.execution.domain.ReplicationRun;
import com.datarelay.execution.domain.ReplicationRunRepository;
import com.datarelay.execution.domain.RunStatus;
import com.datarelay.execution.domain.TargetRun;
import com.datarelay.execution.domain.TargetRunStatus;
import com.datarelay.execution.domain.TriggerType;
import com.datarelay.plan.domain.ReplicationMode;
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
public class JdbcReplicationRunRepository implements ReplicationRunRepository {

    private static final String SELECIONAR_EXECUCAO = """
        SELECT id, plano_id, execucao_origem_id, conector_destino_restrito_id,
               chave_idempotencia, tipo_disparo, modo, status, iniciado_em,
               finalizado_em, linhas_lidas, linhas_escritas, motivo_falha, criado_em
        FROM execucoes_replicacao
        """;

    private static final RowMapper<ReplicationRun> MAPEADOR_EXECUCAO =
        (conjuntoResultados, numeroLinha) -> new ReplicationRun(
        conjuntoResultados.getObject("id", UUID.class),
        conjuntoResultados.getObject("plano_id", UUID.class),
        conjuntoResultados.getObject("execucao_origem_id", UUID.class),
        conjuntoResultados.getObject("conector_destino_restrito_id", UUID.class),
        conjuntoResultados.getString("chave_idempotencia"),
        TriggerType.valueOf(conjuntoResultados.getString("tipo_disparo")),
        ReplicationMode.valueOf(conjuntoResultados.getString("modo")),
        RunStatus.valueOf(conjuntoResultados.getString("status")),
        paraInstante(conjuntoResultados.getTimestamp("iniciado_em")),
        paraInstante(conjuntoResultados.getTimestamp("finalizado_em")),
        conjuntoResultados.getLong("linhas_lidas"),
        conjuntoResultados.getLong("linhas_escritas"),
        conjuntoResultados.getString("motivo_falha"),
        conjuntoResultados.getTimestamp("criado_em").toInstant(),
        List.of());

    private final JdbcClient clienteJdbc;

    public JdbcReplicationRunRepository(JdbcClient clienteJdbc) {
        this.clienteJdbc = clienteJdbc;
    }

    @Override
    public CreatedRun criarSeAusente(UUID planoId, String chaveIdempotencia, TriggerType tipoDisparo,
                                     ReplicationMode modo, UUID execucaoOrigemId,
                                     UUID conectorDestinoRestritoId, Instant agora) {
        UUID id = UUID.randomUUID();
        int inserida = clienteJdbc.sql("""
                INSERT INTO execucoes_replicacao
                    (id, plano_id, execucao_origem_id, conector_destino_restrito_id,
                     chave_idempotencia, tipo_disparo, modo, status, criado_em)
                VALUES
                    (:id, :planoId, :execucaoOrigemId, :destinoRestritoId,
                     :chaveIdempotencia, :tipoDisparo, :modo, 'NA_FILA', :criadoEm)
                ON CONFLICT (plano_id, chave_idempotencia) DO NOTHING
                """)
            .param("id", id)
            .param("planoId", planoId)
            .param("execucaoOrigemId", execucaoOrigemId)
            .param("destinoRestritoId", conectorDestinoRestritoId)
            .param("chaveIdempotencia", chaveIdempotencia)
            .param("tipoDisparo", tipoDisparo.name())
            .param("modo", modo.name())
            .param("criadoEm", Timestamp.from(agora))
            .update();

        ReplicationRun execucao = clienteJdbc.sql(SELECIONAR_EXECUCAO + """
                WHERE plano_id = :planoId AND chave_idempotencia = :chaveIdempotencia
                """)
            .param("planoId", planoId)
            .param("chaveIdempotencia", chaveIdempotencia)
            .query(MAPEADOR_EXECUCAO)
            .single();
        return new CreatedRun(montarDestinos(execucao), inserida == 1);
    }

    @Override
    public Optional<ReplicationRun> buscarPorId(UUID id) {
        return clienteJdbc.sql(SELECIONAR_EXECUCAO + " WHERE id = :id")
            .param("id", id)
            .query(MAPEADOR_EXECUCAO)
            .optional()
            .map(this::montarDestinos);
    }

    @Override
    public List<ReplicationRun> buscarPorPlanoId(UUID planoId, int limite) {
        return clienteJdbc.sql(SELECIONAR_EXECUCAO + """
                WHERE plano_id = :planoId ORDER BY criado_em DESC LIMIT :limite
                """)
            .param("planoId", planoId)
            .param("limite", limite)
            .query(MAPEADOR_EXECUCAO)
            .list()
            .stream()
            .map(this::montarDestinos)
            .toList();
    }

    @Override
    public List<UUID> buscarIdsNaFila(int limite) {
        return clienteJdbc.sql("""
                SELECT id FROM execucoes_replicacao
                WHERE status = 'NA_FILA'
                ORDER BY criado_em
                LIMIT :limite
                """)
            .param("limite", limite)
            .query((conjuntoResultados, numeroLinha) -> conjuntoResultados.getObject("id", UUID.class))
            .list();
    }

    @Override
    public List<ReplicationRun> buscarEmExecucao(int limite) {
        return clienteJdbc.sql(SELECIONAR_EXECUCAO + """
                WHERE status = 'EM_EXECUCAO'
                ORDER BY iniciado_em
                LIMIT :limite
                """)
            .param("limite", limite)
            .query(MAPEADOR_EXECUCAO)
            .list()
            .stream()
            .map(this::montarDestinos)
            .toList();
    }

    @Override
    public boolean existePendenteOuEmExecucao(UUID planoId) {
        return clienteJdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM execucoes_replicacao
                    WHERE plano_id = :planoId AND status IN ('NA_FILA', 'EM_EXECUCAO')
                )
                """)
            .param("planoId", planoId)
            .query(Boolean.class)
            .single();
    }

    @Override
    public boolean marcarEmExecucao(UUID execucaoId, Instant iniciadoEm) {
        return clienteJdbc.sql("""
                UPDATE execucoes_replicacao SET status = 'EM_EXECUCAO', iniciado_em = :iniciadoEm
                WHERE id = :execucaoId AND status = 'NA_FILA'
                """)
            .param("execucaoId", execucaoId)
            .param("iniciadoEm", Timestamp.from(iniciadoEm))
            .update() == 1;
    }

    @Override
    public boolean cancelarNaFila(UUID execucaoId, Instant finalizadoEm) {
        return clienteJdbc.sql("""
                UPDATE execucoes_replicacao
                SET status = 'CANCELADA', finalizado_em = :finalizadoEm,
                    motivo_falha = 'Cancelada antes do inicio do processamento'
                WHERE id = :execucaoId AND status = 'NA_FILA'
                """)
            .param("execucaoId", execucaoId)
            .param("finalizadoEm", Timestamp.from(finalizadoEm))
            .update() == 1;
    }

    @Override
    public void concluir(UUID execucaoId, RunStatus status, long linhasLidas, long linhasEscritas,
                         String motivoFalha, Instant finalizadoEm) {
        clienteJdbc.sql("""
                UPDATE execucoes_replicacao
                SET status = :status, linhas_lidas = :linhasLidas, linhas_escritas = :linhasEscritas,
                    motivo_falha = :motivoFalha, finalizado_em = :finalizadoEm
                WHERE id = :execucaoId
                """)
            .param("execucaoId", execucaoId)
            .param("status", status.name())
            .param("linhasLidas", linhasLidas)
            .param("linhasEscritas", linhasEscritas)
            .param("motivoFalha", motivoFalha)
            .param("finalizadoEm", Timestamp.from(finalizadoEm))
            .update();
    }

    @Override
    public TargetRun iniciarDestino(UUID execucaoId, UUID conectorDestinoId, Instant iniciadoEm) {
        UUID id = UUID.randomUUID();
        UUID idPersistido = clienteJdbc.sql("""
                INSERT INTO execucoes_destino
                    (id, execucao_id, conector_destino_id, status, iniciado_em)
                VALUES
                    (:id, :execucaoId, :destinoId, 'EM_EXECUCAO', :iniciadoEm)
                ON CONFLICT (execucao_id, conector_destino_id)
                DO UPDATE SET
                    status = 'EM_EXECUCAO', iniciado_em = EXCLUDED.iniciado_em,
                    finalizado_em = NULL, linhas_lidas = 0, linhas_escritas = 0, motivo_falha = NULL
                RETURNING id
                """)
            .param("id", id)
            .param("execucaoId", execucaoId)
            .param("destinoId", conectorDestinoId)
            .param("iniciadoEm", Timestamp.from(iniciadoEm))
            .query(UUID.class)
            .single();
        return new TargetRun(idPersistido, execucaoId, conectorDestinoId, TargetRunStatus.EM_EXECUCAO,
            iniciadoEm, null, 0, 0, null);
    }

    @Override
    public void concluirDestino(UUID execucaoDestinoId, TargetRunStatus status, long linhasLidas,
                               long linhasEscritas, String motivoFalha, Instant finalizadoEm) {
        clienteJdbc.sql("""
                UPDATE execucoes_destino
                SET status = :status, linhas_lidas = :linhasLidas, linhas_escritas = :linhasEscritas,
                    motivo_falha = :motivoFalha, finalizado_em = :finalizadoEm
                WHERE id = :execucaoDestinoId
                """)
            .param("execucaoDestinoId", execucaoDestinoId)
            .param("status", status.name())
            .param("linhasLidas", linhasLidas)
            .param("linhasEscritas", linhasEscritas)
            .param("motivoFalha", motivoFalha)
            .param("finalizadoEm", Timestamp.from(finalizadoEm))
            .update();
    }

    @Override
    @Transactional
    public void prepararRetomada(UUID execucaoId, String motivo, Instant agora) {
        clienteJdbc.sql("""
                UPDATE execucoes_destino
                SET status = 'FALHOU', motivo_falha = :motivo, finalizado_em = :agora
                WHERE execucao_id = :execucaoId AND status = 'EM_EXECUCAO'
                """)
            .param("execucaoId", execucaoId)
            .param("motivo", motivo)
            .param("agora", Timestamp.from(agora))
            .update();
        clienteJdbc.sql("""
                UPDATE execucoes_replicacao
                SET status = 'NA_FILA', iniciado_em = NULL, finalizado_em = NULL,
                    linhas_lidas = 0, linhas_escritas = 0, motivo_falha = :motivo
                WHERE id = :execucaoId AND status = 'EM_EXECUCAO'
                """)
            .param("execucaoId", execucaoId)
            .param("motivo", motivo)
            .update();
    }

    private ReplicationRun montarDestinos(ReplicationRun execucao) {
        List<TargetRun> destinos = clienteJdbc.sql("""
                SELECT id, execucao_id, conector_destino_id, status, iniciado_em, finalizado_em,
                       linhas_lidas, linhas_escritas, motivo_falha
                FROM execucoes_destino WHERE execucao_id = :execucaoId ORDER BY iniciado_em
                """)
            .param("execucaoId", execucao.id())
            .query((conjuntoResultados, numeroLinha) -> new TargetRun(
                conjuntoResultados.getObject("id", UUID.class),
                conjuntoResultados.getObject("execucao_id", UUID.class),
                conjuntoResultados.getObject("conector_destino_id", UUID.class),
                TargetRunStatus.valueOf(conjuntoResultados.getString("status")),
                conjuntoResultados.getTimestamp("iniciado_em").toInstant(),
                paraInstante(conjuntoResultados.getTimestamp("finalizado_em")),
                conjuntoResultados.getLong("linhas_lidas"),
                conjuntoResultados.getLong("linhas_escritas"),
                conjuntoResultados.getString("motivo_falha")))
            .list();
        return new ReplicationRun(
            execucao.id(), execucao.planoId(), execucao.execucaoOrigemId(), execucao.conectorDestinoRestritoId(),
            execucao.chaveIdempotencia(), execucao.tipoDisparo(), execucao.modo(), execucao.status(),
            execucao.iniciadoEm(), execucao.finalizadoEm(), execucao.linhasLidas(), execucao.linhasEscritas(), execucao.motivoFalha(),
            execucao.criadoEm(), destinos);
    }

    private static Instant paraInstante(Timestamp dataHora) {
        return dataHora == null ? null : dataHora.toInstant();
    }
}
