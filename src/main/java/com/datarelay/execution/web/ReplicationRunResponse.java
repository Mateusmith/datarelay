package com.datarelay.execution.web;

import com.datarelay.execution.domain.ReplicationRun;
import com.datarelay.execution.domain.RunStatus;
import com.datarelay.execution.domain.TargetRun;
import com.datarelay.execution.domain.TargetRunStatus;
import com.datarelay.execution.domain.TriggerType;
import com.datarelay.plan.domain.ReplicationMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "RespostaExecucaoReplicacao")
public record ReplicationRunResponse(
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
    List<RespostaExecucaoDestino> destinos
) {
    static ReplicationRunResponse de(ReplicationRun execucao) {
        return new ReplicationRunResponse(
            execucao.id(), execucao.planoId(), execucao.execucaoOrigemId(), execucao.conectorDestinoRestritoId(),
            execucao.chaveIdempotencia(), execucao.tipoDisparo(), execucao.modo(), execucao.status(),
            execucao.iniciadoEm(), execucao.finalizadoEm(), execucao.linhasLidas(), execucao.linhasEscritas(), execucao.motivoFalha(),
            execucao.criadoEm(), execucao.destinos().stream().map(RespostaExecucaoDestino::de).toList());
    }

    @Schema(name = "RespostaExecucaoDestino")
    public record RespostaExecucaoDestino(
        UUID id,
        UUID conectorDestinoId,
        TargetRunStatus status,
        Instant iniciadoEm,
        Instant finalizadoEm,
        long linhasLidas,
        long linhasEscritas,
        String motivoFalha
    ) {
        static RespostaExecucaoDestino de(TargetRun execucao) {
            return new RespostaExecucaoDestino(
                execucao.id(), execucao.conectorDestinoId(), execucao.status(), execucao.iniciadoEm(), execucao.finalizadoEm(),
                execucao.linhasLidas(), execucao.linhasEscritas(), execucao.motivoFalha());
        }
    }
}
