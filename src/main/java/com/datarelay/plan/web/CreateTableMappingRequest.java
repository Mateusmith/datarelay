package com.datarelay.plan.web;

import com.datarelay.plan.application.CreateTableMappingCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "CriarMapeamentoTabela")
public record CreateTableMappingRequest(
    @NotBlank @Pattern(regexp = "[a-z_][a-z0-9_]*") String esquemaOrigem,
    @NotBlank @Pattern(regexp = "[a-z_][a-z0-9_]*") String tabelaOrigem,
    @NotBlank @Pattern(regexp = "[a-z_][a-z0-9_]*") String esquemaDestino,
    @NotBlank @Pattern(regexp = "[a-z_][a-z0-9_]*") String tabelaDestino,
    @NotBlank @Pattern(regexp = "[a-z_][a-z0-9_]*") String colunaChave,
    @Pattern(regexp = "[a-z_][a-z0-9_]*") String colunaIncremental,
    @NotEmpty @Size(max = 200) List<@Pattern(regexp = "[a-z_][a-z0-9_]*") String> colunas
) {
    CreateTableMappingCommand paraComando() {
        return new CreateTableMappingCommand(
            esquemaOrigem, tabelaOrigem, esquemaDestino, tabelaDestino, colunaChave, colunaIncremental, colunas);
    }
}
