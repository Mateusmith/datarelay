package com.datarelay.connector.application;

import com.datarelay.connector.domain.ConnectorRole;

public record CreateConnectorCommand(
    String nome,
    ConnectorRole papel,
    String urlJdbc,
    String usuario,
    String referenciaSegredo
) {
}
