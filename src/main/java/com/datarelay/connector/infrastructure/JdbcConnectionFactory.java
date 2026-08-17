package com.datarelay.connector.infrastructure;

import com.datarelay.connector.domain.Connector;
import com.datarelay.shared.domain.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

@Component
public class JdbcConnectionFactory {

    private final SecretProvider provedorSegredos;
    private final int tempoLimiteConexaoSegundos;

    public JdbcConnectionFactory(SecretProvider provedorSegredos,
                                 @Value("${datarelay.conexao.tempo-limite-segundos:5}") int tempoLimiteConexaoSegundos) {
        this.provedorSegredos = provedorSegredos;
        this.tempoLimiteConexaoSegundos = tempoLimiteConexaoSegundos;
    }

    public Connection abrir(Connector conector) throws SQLException {
        if (!conector.ativo()) {
            throw new DomainException("O conector esta desativado: " + conector.nome());
        }
        Properties propriedades = new Properties();
        propriedades.setProperty("user", conector.usuario());
        propriedades.setProperty("password", provedorSegredos.resolver(conector.referenciaSegredo()));
        propriedades.setProperty("connectTimeout", Integer.toString(tempoLimiteConexaoSegundos));
        propriedades.setProperty("ApplicationName", "Replicador de Dados");
        return DriverManager.getConnection(conector.urlJdbc(), propriedades);
    }
}
