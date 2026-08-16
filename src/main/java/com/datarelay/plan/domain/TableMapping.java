package com.datarelay.plan.domain;

import com.datarelay.shared.domain.DomainException;
import com.datarelay.shared.domain.SqlIdentifier;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TableMapping(
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
    public TableMapping {
        Objects.requireNonNull(id, "O id do mapeamento e obrigatorio");
        if (ordem < 0) {
            throw new DomainException("A ordem do mapeamento nao pode ser negativa");
        }
        esquemaOrigem = SqlIdentifier.exigirValido(esquemaOrigem, "Esquema de origem");
        tabelaOrigem = SqlIdentifier.exigirValido(tabelaOrigem, "Tabela de origem");
        esquemaDestino = SqlIdentifier.exigirValido(esquemaDestino, "Esquema de destino");
        tabelaDestino = SqlIdentifier.exigirValido(tabelaDestino, "Tabela de destino");
        colunaChave = SqlIdentifier.exigirValido(colunaChave, "Coluna-chave");
        if (colunaIncremental != null) {
            colunaIncremental = SqlIdentifier.exigirValido(colunaIncremental, "Coluna incremental");
        }
        if (colunas == null || colunas.isEmpty()) {
            throw new DomainException("O mapeamento de tabela deve conter pelo menos uma coluna");
        }
        colunas = colunas.stream()
            .map(coluna -> SqlIdentifier.exigirValido(coluna, "Coluna mapeada"))
            .distinct()
            .toList();
        if (!colunas.contains(colunaChave)) {
            throw new DomainException("As colunas mapeadas devem incluir a coluna-chave: " + colunaChave);
        }
        if (colunaIncremental != null && !colunas.contains(colunaIncremental)) {
            throw new DomainException("As colunas mapeadas devem incluir a coluna incremental: " + colunaIncremental);
        }
    }
}
