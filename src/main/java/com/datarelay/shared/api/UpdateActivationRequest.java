package com.datarelay.shared.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "AlterarAtivacao")
public record UpdateActivationRequest(@NotNull Boolean ativo) {

    public boolean valor() {
        return Boolean.TRUE.equals(ativo);
    }
}
