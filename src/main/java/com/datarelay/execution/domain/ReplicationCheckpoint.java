package com.datarelay.execution.domain;

import java.time.Instant;
import java.util.UUID;

public record ReplicationCheckpoint(
    UUID planoId,
    UUID conectorDestinoId,
    UUID mapeamentoId,
    Instant ultimoValorIncremental,
    Long ultimoValorChave,
    Instant atualizadoEm
) {
}
