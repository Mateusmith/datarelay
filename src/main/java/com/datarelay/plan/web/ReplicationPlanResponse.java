package com.datarelay.plan.web;

import com.datarelay.plan.domain.ReplicationMode;
import com.datarelay.plan.domain.ReplicationPlan;
import com.datarelay.plan.domain.TableMapping;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "RespostaPlanoReplicacao")
public record ReplicationPlanResponse(
    UUID id,
    String nome,
    UUID conectorOrigemId,
    List<UUID> idsConectoresDestino,
    ReplicationMode modoPadrao,
    int tamanhoLote,
    String expressaoCron,
    Instant proximaExecucaoEm,
    boolean ativo,
    List<RespostaMapeamentoTabela> mapeamentos,
    Instant criadoEm,
    Instant atualizadoEm
) {
    static ReplicationPlanResponse de(ReplicationPlan plano) {
        return new ReplicationPlanResponse(
            plano.id(),
            plano.nome(),
            plano.conectorOrigemId(),
            plano.idsConectoresDestino(),
            plano.modoPadrao(),
            plano.tamanhoLote(),
            plano.expressaoCron(),
            plano.proximaExecucaoEm(),
            plano.ativo(),
            plano.mapeamentos().stream().map(RespostaMapeamentoTabela::de).toList(),
            plano.criadoEm(),
            plano.atualizadoEm());
    }

    @Schema(name = "RespostaMapeamentoTabela")
    public record RespostaMapeamentoTabela(
        UUID id,
        int ordem,
        String esquemaOrigem,
        String tabelaOrigem,
        String esquemaDestino,
        String tabelaDestino,
        String colunaChave,
        String colunaIncremental,
        List<String> colunas,
        boolean ativo
    ) {
        static RespostaMapeamentoTabela de(TableMapping mapeamento) {
            return new RespostaMapeamentoTabela(
                mapeamento.id(), mapeamento.ordem(), mapeamento.esquemaOrigem(), mapeamento.tabelaOrigem(),
                mapeamento.esquemaDestino(), mapeamento.tabelaDestino(), mapeamento.colunaChave(),
                mapeamento.colunaIncremental(), mapeamento.colunas(), mapeamento.ativo());
        }
    }
}
