package com.datarelay.execution.web;

import com.datarelay.plan.domain.ReplicationMode;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "IniciarExecucao")
public record StartRunRequest(ReplicationMode modo) {
}
