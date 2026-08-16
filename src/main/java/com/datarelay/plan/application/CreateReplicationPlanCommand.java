package com.datarelay.plan.application;

import com.datarelay.plan.domain.ReplicationMode;

import java.util.List;
import java.util.UUID;

public record CreateReplicationPlanCommand(
    String nome,
    UUID conectorOrigemId,
    List<UUID> idsConectoresDestino,
    ReplicationMode modoPadrao,
    int tamanhoLote,
    String expressaoCron,
    List<CreateTableMappingCommand> mapeamentos
) {
}
