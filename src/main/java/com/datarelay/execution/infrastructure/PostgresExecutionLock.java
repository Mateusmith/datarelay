package com.datarelay.execution.infrastructure;

import com.datarelay.execution.domain.ExecutionLease;
import com.datarelay.execution.domain.ExecutionLock;
import com.datarelay.shared.domain.InfrastructureException;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@Component
public class PostgresExecutionLock implements ExecutionLock {

    private static final String ADQUIRIR = "SELECT pg_try_advisory_lock(hashtextextended(?, 0))";
    private static final String LIBERAR = "SELECT pg_advisory_unlock(hashtextextended(?, 0))";

    private final DataSource fonteDados;

    public PostgresExecutionLock(DataSource fonteDados) {
        this.fonteDados = fonteDados;
    }

    @Override
    public Optional<ExecutionLease> tentarAdquirir(UUID planoId) {
        Connection conexao = null;
        try {
            conexao = fonteDados.getConnection();
            try (PreparedStatement instrucao = conexao.prepareStatement(ADQUIRIR)) {
                instrucao.setString(1, planoId.toString());
                try (ResultSet resultado = instrucao.executeQuery()) {
                    resultado.next();
                    if (!resultado.getBoolean(1)) {
                        conexao.close();
                        return Optional.empty();
                    }
                }
            }
            return Optional.of(new PostgresLease(conexao, planoId));
        } catch (SQLException excecao) {
            fecharSilenciosamente(conexao);
            throw new InfrastructureException("Nao foi possivel adquirir a trava do plano " + planoId, excecao);
        }
    }

    private static void fecharSilenciosamente(Connection conexao) {
        if (conexao == null) {
            return;
        }
        try {
            conexao.close();
        } catch (SQLException ignorada) {
            // A conexao ja esta inutilizavel; nao ha acao adicional segura.
        }
    }

    private static final class PostgresLease implements ExecutionLease {

        private final Connection conexao;
        private final UUID planoId;
        private boolean fechada;

        private PostgresLease(Connection conexao, UUID planoId) {
            this.conexao = conexao;
            this.planoId = planoId;
        }

        @Override
        public void close() {
            if (fechada) {
                return;
            }
            fechada = true;
            try (PreparedStatement instrucao = conexao.prepareStatement(LIBERAR)) {
                instrucao.setString(1, planoId.toString());
                instrucao.executeQuery();
            } catch (SQLException excecao) {
                throw new InfrastructureException("Nao foi possivel liberar a trava do plano " + planoId, excecao);
            } finally {
                fecharSilenciosamente(conexao);
            }
        }
    }
}
