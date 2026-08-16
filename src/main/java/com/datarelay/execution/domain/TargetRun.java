package com.datarelay.execution.domain;

import java.time.Instant;
import java.util.UUID;

public record TargetRun(
    UUID id,
    UUID execucaoId,
    UUID conectorDestinoId,
    TargetRunStatus status,
    Instant iniciadoEm,
    Instant finalizadoEm,
    long linhasLidas,
    long linhasEscritas,
    String motivoFalha
) {
}
