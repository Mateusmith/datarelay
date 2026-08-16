package com.datarelay.execution.domain;

import com.datarelay.plan.domain.ReplicationMode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReplicationRunRepository {

    CreatedRun criarSeAusente(UUID planoId, String chaveIdempotencia, TriggerType tipoDisparo,
                              ReplicationMode modo, UUID execucaoOrigemId,
                              UUID conectorDestinoRestritoId, Instant agora);

    Optional<ReplicationRun> buscarPorId(UUID id);

    List<ReplicationRun> buscarPorPlanoId(UUID planoId, int limite);

    List<UUID> buscarIdsNaFila(int limite);

    List<ReplicationRun> buscarEmExecucao(int limite);

    boolean existePendenteOuEmExecucao(UUID planoId);

    boolean marcarEmExecucao(UUID execucaoId, Instant iniciadoEm);

    boolean cancelarNaFila(UUID execucaoId, Instant finalizadoEm);

    void concluir(UUID execucaoId, RunStatus status, long linhasLidas, long linhasEscritas,
                  String motivoFalha, Instant finalizadoEm);

    TargetRun iniciarDestino(UUID execucaoId, UUID conectorDestinoId, Instant iniciadoEm);

    void concluirDestino(UUID execucaoDestinoId, TargetRunStatus status, long linhasLidas,
                        long linhasEscritas, String motivoFalha, Instant finalizadoEm);

    void prepararRetomada(UUID execucaoId, String motivo, Instant agora);
}
