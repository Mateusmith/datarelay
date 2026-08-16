package com.datarelay.connector.application;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ResultadoTesteConexao")
public record ConnectionProbeResult(boolean acessivel, long latenciaMilissegundos, String mensagem) {
}
