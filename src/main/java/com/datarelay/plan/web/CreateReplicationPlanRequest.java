package com.datarelay.plan.web;

import com.datarelay.plan.application.CreateReplicationPlanCommand;
import com.datarelay.plan.domain.ReplicationMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(name = "CriarPlanoReplicacao")
public record CreateReplicationPlanRequest(
    @NotBlank @Size(max = 120) String nome,
    @NotNull UUID conectorOrigemId,
    @NotEmpty List<@NotNull UUID> idsConectoresDestino,
    @NotNull ReplicationMode modoPadrao,
    @Min(1) @Max(10_000) int tamanhoLote,
    @Size(max = 100) String expressaoCron,
    @NotEmpty List<@Valid CreateTableMappingRequest> mapeamentos
) {
    CreateReplicationPlanCommand paraComando() {
        return new CreateReplicationPlanCommand(
            nome,
            conectorOrigemId,
            idsConectoresDestino,
            modoPadrao,
            tamanhoLote,
            expressaoCron,
            mapeamentos.stream().map(CreateTableMappingRequest::paraComando).toList());
    }
}
