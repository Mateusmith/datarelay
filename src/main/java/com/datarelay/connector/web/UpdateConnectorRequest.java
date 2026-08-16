package com.datarelay.connector.web;

import com.datarelay.connector.application.UpdateConnectorCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "AtualizarConector")
public record UpdateConnectorRequest(
    @NotBlank @Size(max = 100) String nome,
    @NotBlank @Size(max = 500) @Pattern(regexp = "^jdbc:postgresql://.+") String urlJdbc,
    @NotBlank @Size(max = 100) String usuario,
    @NotBlank @Size(max = 150) @Pattern(regexp = "^env:[A-Z][A-Z0-9_]*$") String referenciaSegredo
) {
    UpdateConnectorCommand paraComando() {
        return new UpdateConnectorCommand(nome, urlJdbc, usuario, referenciaSegredo);
    }
}
