package com.datarelay.connector.web;

import com.datarelay.connector.application.CreateConnectorCommand;
import com.datarelay.connector.domain.ConnectorRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "CriarConector")
public record CreateConnectorRequest(
    @NotBlank @Size(max = 100) String nome,
    @NotNull ConnectorRole papel,
    @NotBlank @Size(max = 500) @Pattern(regexp = "^jdbc:postgresql://.+") String urlJdbc,
    @NotBlank @Size(max = 100) String usuario,
    @NotBlank @Size(max = 150) @Pattern(regexp = "^env:[A-Z][A-Z0-9_]*$") String referenciaSegredo
) {
    CreateConnectorCommand paraComando() {
        return new CreateConnectorCommand(nome, papel, urlJdbc, usuario, referenciaSegredo);
    }
}
