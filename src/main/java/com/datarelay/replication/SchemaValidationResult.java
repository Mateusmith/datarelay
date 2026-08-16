package com.datarelay.replication;

import java.util.List;

public record SchemaValidationResult(boolean valido, List<String> erros) {

    public SchemaValidationResult {
        erros = erros == null ? List.of() : List.copyOf(erros);
    }

    public static SchemaValidationResult sucesso() {
        return new SchemaValidationResult(true, List.of());
    }

    public static SchemaValidationResult falha(List<String> erros) {
        return new SchemaValidationResult(false, erros);
    }
}
