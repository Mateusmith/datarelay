package com.datarelay.execution.domain;

import java.util.Optional;
import java.util.UUID;

public interface CheckpointRepository {

    Optional<ReplicationCheckpoint> buscar(UUID planoId, UUID conectorDestinoId, UUID mapeamentoId);

    void salvar(ReplicationCheckpoint pontoControle);
}
