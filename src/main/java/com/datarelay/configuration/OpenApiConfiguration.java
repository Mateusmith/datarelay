package com.datarelay.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI documentacaoApiDataRelay(
        @Value("${DATARELAY_URL_AUTORIZACAO:http://localhost:18081/realms/datarelay/protocol/openid-connect/auth}")
        String urlAutorizacao,
        @Value("${DATARELAY_URL_TOKEN:http://localhost:18081/realms/datarelay/protocol/openid-connect/token}")
        String urlToken) {
        String nomeEsquema = "oauth2";
        SecurityScheme esquema = new SecurityScheme()
            .type(SecurityScheme.Type.OAUTH2)
            .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                .authorizationUrl(urlAutorizacao)
                .tokenUrl(urlToken)
                .scopes(new Scopes()
                    .addString("datarelay.leitura", "Consultar conectores, planos e execucoes")
                    .addString("datarelay.escrita", "Criar configuracoes e iniciar execucoes"))));
        return new OpenAPI()
            .info(new Info()
                .title("DataRelay API")
                .version("v1")
                .description("Replicacao PostgreSQL retomavel com destinos isolados"))
            .components(new Components().addSecuritySchemes(nomeEsquema, esquema))
            .addSecurityItem(new SecurityRequirement().addList(nomeEsquema));
    }
}
