package com.datarelay.connector.application;

public record UpdateConnectorCommand(
    String nome,
    String urlJdbc,
    String usuario,
    String referenciaSegredo
) {
}
