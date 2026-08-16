package com.datarelay.execution.domain;

import com.datarelay.plan.domain.ReplicationMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReplicationRun(
    UUID id,
    UUID planoId,
    UUID execucaoOrigemId,
    UUID conectorDestinoRestritoId,
    String chaveIdempotencia,
    TriggerType tipoDisparo,
    ReplicationMode modo,
    RunStatus status,
    Instant iniciadoEm,
    Instant finalizadoEm,
    long linhasLidas,
    long linhasEscritas,
    String motivoFalha,
    Instant criadoEm,
    List<TargetRun> destinos
) {
}
