package com.datarelay.connector.web;

import com.datarelay.connector.domain.Connector;
import com.datarelay.connector.domain.ConnectorRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "RespostaConector")
public record ConnectorResponse(
    UUID id,
    String nome,
    ConnectorRole papel,
    String urlJdbc,
    String usuario,
    String referenciaSegredo,
    boolean ativo,
    Instant criadoEm,
    Instant atualizadoEm
) {
    static ConnectorResponse de(Connector conector) {
        return new ConnectorResponse(
            conector.id(),
            conector.nome(),
            conector.papel(),
            conector.urlJdbc(),
            conector.usuario(),
            conector.referenciaSegredo(),
            conector.ativo(),
            conector.criadoEm(),
            conector.atualizadoEm());
    }
}
