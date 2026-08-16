package com.datarelay.configuration;

import com.datarelay.connector.application.ConnectorService;
import com.datarelay.connector.domain.Connector;
import com.datarelay.connector.domain.ConnectorRole;
import com.datarelay.connector.web.ConnectorController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConnectorController.class)
@Import(SecurityConfiguration.class)
class ApiSecurityTest {

    @Autowired
    MockMvc clienteHttp;

    @MockitoBean
    ConnectorService servicoConectores;

    @MockitoBean
    JwtDecoder decodificadorJwt;

    @BeforeEach
    void configurarServico() {
        when(servicoConectores.listar()).thenReturn(List.of());
    }

    @Test
    void rejeitaApiSemToken() throws Exception {
        clienteHttp.perform(get("/api/v1/conectores"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void permiteConsultaComEscopoDeLeitura() throws Exception {
        clienteHttp.perform(get("/api/v1/conectores")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_datarelay.leitura"))))
            .andExpect(status().isOk());
    }

    @Test
    void respondeQuatrocentosQuandoUuidDaUrlForInvalido() throws Exception {
        clienteHttp.perform(get("/api/v1/conectores/nao-e-uuid")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_datarelay.leitura"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Requisicao invalida"))
            .andExpect(jsonPath("$.detail").value("Parametro de URL invalido: id"));
    }

    @Test
    void bloqueiaEscritaQuandoTokenPossuiSomenteLeitura() throws Exception {
        clienteHttp.perform(post("/api/v1/conectores")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_datarelay.leitura")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonConector()))
            .andExpect(status().isForbidden());
    }

    @Test
    void permiteCriacaoComEscopoDeEscrita() throws Exception {
        Instant agora = Instant.parse("2026-08-16T12:00:00Z");
        Connector conector = Connector.criar(
            UUID.randomUUID(), "origem", ConnectorRole.ORIGEM,
            "jdbc:postgresql://origem:5432/dados", "usuario", "env:SENHA_ORIGEM", agora);
        when(servicoConectores.criar(any())).thenReturn(conector);

        clienteHttp.perform(post("/api/v1/conectores")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_datarelay.escrita")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonConector()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(conector.id().toString()))
            .andExpect(jsonPath("$.papel").value("ORIGEM"));
    }

    private String jsonConector() {
        return """
            {
              "nome": "origem",
              "papel": "ORIGEM",
              "urlJdbc": "jdbc:postgresql://origem:5432/dados",
              "usuario": "usuario",
              "referenciaSegredo": "env:SENHA_ORIGEM"
            }
            """;
    }
}
