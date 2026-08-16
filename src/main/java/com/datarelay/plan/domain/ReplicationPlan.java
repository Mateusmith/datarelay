package com.datarelay.plan.domain;

import com.datarelay.shared.domain.DomainException;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ReplicationPlan(
    UUID id,
    String nome,
    UUID conectorOrigemId,
    List<UUID> idsConectoresDestino,
    ReplicationMode modoPadrao,
    int tamanhoLote,
    String expressaoCron,
    Instant proximaExecucaoEm,
    boolean ativo,
    List<TableMapping> mapeamentos,
    Instant criadoEm,
    Instant atualizadoEm
) {
    public ReplicationPlan {
        Objects.requireNonNull(id, "O id do plano e obrigatorio");
        Objects.requireNonNull(conectorOrigemId, "O conector de origem e obrigatorio");
        Objects.requireNonNull(modoPadrao, "O modo padrao e obrigatorio");
        Objects.requireNonNull(criadoEm, "A data de criacao do plano e obrigatoria");
        Objects.requireNonNull(atualizadoEm, "A data de atualizacao do plano e obrigatoria");
        if (nome == null || nome.isBlank()) {
            throw new DomainException("O nome do plano e obrigatorio");
        }
        nome = nome.trim();
        if (idsConectoresDestino == null || idsConectoresDestino.isEmpty()) {
            throw new DomainException("O plano deve possuir pelo menos um conector de destino");
        }
        idsConectoresDestino = idsConectoresDestino.stream().distinct().toList();
        if (idsConectoresDestino.contains(conectorOrigemId)) {
            throw new DomainException("O conector de origem nao pode ser tambem um destino");
        }
        if (tamanhoLote < 1 || tamanhoLote > 10_000) {
            throw new DomainException("O tamanho do lote deve estar entre 1 e 10000");
        }
        if (mapeamentos == null || mapeamentos.isEmpty()) {
            throw new DomainException("O plano deve possuir pelo menos um mapeamento de tabela");
        }
        mapeamentos = List.copyOf(mapeamentos);
        if (modoPadrao == ReplicationMode.INCREMENTAL
            && mapeamentos.stream().anyMatch(mapeamento -> mapeamento.colunaIncremental() == null)) {
            throw new DomainException("Planos incrementais exigem uma coluna incremental em cada mapeamento");
        }
    }
}
