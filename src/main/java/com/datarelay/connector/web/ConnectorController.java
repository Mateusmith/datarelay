package com.datarelay.connector.web;

import com.datarelay.connector.application.ConnectionProbeResult;
import com.datarelay.connector.application.ConnectorService;
import com.datarelay.shared.api.UpdateActivationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conectores")
@Tag(name = "Conectores", description = "Cadastro e validacao de conexoes PostgreSQL")
public class ConnectorController {

    private final ConnectorService servico;

    public ConnectorController(ConnectorService servico) {
        this.servico = servico;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar conector")
    ConnectorResponse criar(@Valid @RequestBody CreateConnectorRequest requisicao) {
        return ConnectorResponse.de(servico.criar(requisicao.paraComando()));
    }

    @GetMapping
    @Operation(summary = "Listar conectores")
    List<ConnectorResponse> listar() {
        return servico.listar().stream().map(ConnectorResponse::de).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar conector")
    ConnectorResponse buscar(@PathVariable UUID id) {
        return ConnectorResponse.de(servico.buscar(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar conector inativo ou sem plano ativo")
    ConnectorResponse atualizar(@PathVariable UUID id, @Valid @RequestBody UpdateConnectorRequest requisicao) {
        return ConnectorResponse.de(servico.atualizar(id, requisicao.paraComando()));
    }

    @PatchMapping("/{id}/ativacao")
    @Operation(summary = "Ativar ou desativar conector")
    ConnectorResponse alterarAtivacao(@PathVariable UUID id,
                                       @Valid @RequestBody UpdateActivationRequest requisicao) {
        return ConnectorResponse.de(servico.alterarAtivacao(id, requisicao.valor()));
    }

    @PostMapping("/{id}/teste-conexao")
    @Operation(summary = "Testar conexao do conector")
    ConnectionProbeResult testarConexao(@PathVariable UUID id) {
        return servico.testar(id);
    }
}
