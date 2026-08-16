package com.datarelay.plan.web;

import com.datarelay.plan.application.ReplicationPlanService;
import com.datarelay.replication.SchemaValidationResult;
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
@RequestMapping("/api/v1/planos")
@Tag(name = "Planos de replicacao", description = "Configuracao das regras e tabelas replicadas")
public class ReplicationPlanController {

    private final ReplicationPlanService servico;

    public ReplicationPlanController(ReplicationPlanService servico) {
        this.servico = servico;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar plano de replicacao")
    ReplicationPlanResponse criar(@Valid @RequestBody CreateReplicationPlanRequest requisicao) {
        return ReplicationPlanResponse.de(servico.criar(requisicao.paraComando()));
    }

    @GetMapping
    @Operation(summary = "Listar planos de replicacao")
    List<ReplicationPlanResponse> listar() {
        return servico.listar().stream().map(ReplicationPlanResponse::de).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar plano de replicacao")
    ReplicationPlanResponse buscar(@PathVariable UUID id) {
        return ReplicationPlanResponse.de(servico.buscar(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar plano e reiniciar seus pontos de controle")
    ReplicationPlanResponse atualizar(@PathVariable UUID id,
                                       @Valid @RequestBody CreateReplicationPlanRequest requisicao) {
        return ReplicationPlanResponse.de(servico.atualizar(id, requisicao.paraComando()));
    }

    @PatchMapping("/{id}/ativacao")
    @Operation(summary = "Ativar ou desativar plano")
    ReplicationPlanResponse alterarAtivacao(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateActivationRequest requisicao) {
        return ReplicationPlanResponse.de(servico.alterarAtivacao(id, requisicao.valor()));
    }

    @PostMapping("/{id}/validacao-esquema")
    @Operation(summary = "Validar tabelas, colunas, tipos e dependencias do plano")
    SchemaValidationResult validarEsquema(@PathVariable UUID id) {
        return servico.validarEsquema(id);
    }
}
