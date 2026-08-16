package com.datarelay.plan.application;

import java.util.List;

public record CreateTableMappingCommand(
    String esquemaOrigem,
    String tabelaOrigem,
    String esquemaDestino,
    String tabelaDestino,
    String colunaChave,
    String colunaIncremental,
    List<String> colunas
) {
}
