package com.datarelay.connector.infrastructure;

import com.datarelay.shared.domain.DomainException;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentSecretProvider implements SecretProvider {

    private static final String PREFIXO = "env:";

    private final Environment ambiente;

    public EnvironmentSecretProvider(Environment ambiente) {
        this.ambiente = ambiente;
    }

    @Override
    public String resolver(String referencia) {
        if (referencia == null || !referencia.startsWith(PREFIXO)) {
            throw new DomainException("Referencia de segredo nao suportada");
        }
        String nomeVariavel = referencia.substring(PREFIXO.length());
        String segredo = ambiente.getProperty(nomeVariavel);
        if (segredo == null || segredo.isBlank()) {
            throw new DomainException("A variavel de ambiente do segredo nao foi configurada: " + nomeVariavel);
        }
        return segredo;
    }
}
