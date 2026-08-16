package com.datarelay.execution.web;

import com.datarelay.execution.application.ReplicationExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Execucoes de replicacao", description = "Disparo e acompanhamento das replicacoes")
public class ReplicationRunController {

    private final ReplicationExecutionService servico;

    public ReplicationRunController(ReplicationExecutionService servico) {
        this.servico = servico;
    }

    @PostMapping("/planos/{planoId}/execucoes")
    @Operation(summary = "Iniciar execucao idempotente")
    ResponseEntity<ReplicationRunResponse> iniciar(
        @PathVariable UUID planoId,
        @RequestHeader("Idempotency-Key") String chaveIdempotencia,
        @RequestBody(required = false) StartRunRequest requisicao) {
        var modo = requisicao == null ? null : requisicao.modo();
        var execucao = servico.iniciarManual(planoId, chaveIdempotencia, modo);
        return ResponseEntity.accepted().body(ReplicationRunResponse.de(execucao));
    }

    @PostMapping("/execucoes/{execucaoId}/destinos/{destinoId}/reprocessamentos")
    @Operation(summary = "Reprocessar somente um destino que falhou")
    ResponseEntity<ReplicationRunResponse> reprocessarDestino(
        @PathVariable UUID execucaoId,
        @PathVariable UUID destinoId,
        @RequestHeader("Idempotency-Key") String chaveIdempotencia,
        @RequestBody(required = false) StartRunRequest requisicao) {
        var modo = requisicao == null ? null : requisicao.modo();
        var reprocessamento = servico.reprocessarDestino(execucaoId, destinoId, chaveIdempotencia, modo);
        return ResponseEntity.accepted().body(ReplicationRunResponse.de(reprocessamento));
    }

    @GetMapping("/execucoes/{execucaoId}")
    @Operation(summary = "Buscar execucao")
    ReplicationRunResponse buscar(@PathVariable UUID execucaoId) {
        return ReplicationRunResponse.de(servico.buscar(execucaoId));
    }

    @PostMapping("/execucoes/{execucaoId}/cancelamento")
    @Operation(summary = "Cancelar execucao que ainda esta na fila")
    ReplicationRunResponse cancelar(@PathVariable UUID execucaoId) {
        return ReplicationRunResponse.de(servico.cancelar(execucaoId));
    }

    @GetMapping("/planos/{planoId}/execucoes")
    @Operation(summary = "Listar execucoes do plano")
    List<ReplicationRunResponse> listar(@PathVariable UUID planoId,
                                      @RequestParam(defaultValue = "20") int limite) {
        return servico.listarPorPlano(planoId, limite).stream().map(ReplicationRunResponse::de).toList();
    }
}
