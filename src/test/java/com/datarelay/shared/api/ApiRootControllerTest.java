package com.datarelay.shared.api;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRootControllerTest {

    @Test
    void redirecionaRaizParaDocumentacaoSwagger() {
        var resposta = new ApiRootController().redirecionarParaDocumentacao();

        assertThat(resposta.getStatusCode().value()).isEqualTo(302);
        assertThat(resposta.getHeaders().getLocation()).isEqualTo(URI.create("/swagger-ui.html"));
    }
}
