package com.datarelay.connector.infrastructure;

import com.datarelay.connector.domain.Connector;
import com.datarelay.connector.domain.ConnectorRepository;
import com.datarelay.connector.domain.ConnectorRole;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcConnectorRepository implements ConnectorRepository {

    private static final String COLUNAS_SELECAO = """
        SELECT id, nome, papel, url_jdbc, usuario, referencia_segredo, ativo, criado_em, atualizado_em
        FROM conectores
        """;

    private static final RowMapper<Connector> MAPEADOR_LINHA = (conjuntoResultados, numeroLinha) -> new Connector(
        conjuntoResultados.getObject("id", UUID.class),
        conjuntoResultados.getString("nome"),
        ConnectorRole.valueOf(conjuntoResultados.getString("papel")),
        conjuntoResultados.getString("url_jdbc"),
        conjuntoResultados.getString("usuario"),
        conjuntoResultados.getString("referencia_segredo"),
        conjuntoResultados.getBoolean("ativo"),
        conjuntoResultados.getTimestamp("criado_em").toInstant(),
        conjuntoResultados.getTimestamp("atualizado_em").toInstant());

    private final JdbcClient clienteJdbc;

    public JdbcConnectorRepository(JdbcClient clienteJdbc) {
        this.clienteJdbc = clienteJdbc;
    }

    @Override
    public void salvar(Connector conector) {
        clienteJdbc.sql("""
                INSERT INTO conectores
                    (id, nome, papel, url_jdbc, usuario, referencia_segredo, ativo, criado_em, atualizado_em)
                VALUES
                    (:id, :nome, :papel, :urlJdbc, :usuario, :referenciaSegredo, :ativo, :criadoEm, :atualizadoEm)
                """)
            .param("id", conector.id())
            .param("nome", conector.nome())
            .param("papel", conector.papel().name())
            .param("urlJdbc", conector.urlJdbc())
            .param("usuario", conector.usuario())
            .param("referenciaSegredo", conector.referenciaSegredo())
            .param("ativo", conector.ativo())
            .param("criadoEm", Timestamp.from(conector.criadoEm()))
            .param("atualizadoEm", Timestamp.from(conector.atualizadoEm()))
            .update();
    }

    @Override
    public void atualizar(Connector conector) {
        clienteJdbc.sql("""
                UPDATE conectores
                SET nome = :nome, url_jdbc = :urlJdbc, usuario = :usuario,
                    referencia_segredo = :referenciaSegredo, ativo = :ativo,
                    atualizado_em = :atualizadoEm
                WHERE id = :id
                """)
            .param("id", conector.id())
            .param("nome", conector.nome())
            .param("urlJdbc", conector.urlJdbc())
            .param("usuario", conector.usuario())
            .param("referenciaSegredo", conector.referenciaSegredo())
            .param("ativo", conector.ativo())
            .param("atualizadoEm", Timestamp.from(conector.atualizadoEm()))
            .update();
    }

    @Override
    public Optional<Connector> buscarPorId(UUID id) {
        return clienteJdbc.sql(COLUNAS_SELECAO + " WHERE id = :id")
            .param("id", id)
            .query(MAPEADOR_LINHA)
            .optional();
    }

    @Override
    public Optional<Connector> buscarPorNome(String nome) {
        return clienteJdbc.sql(COLUNAS_SELECAO + " WHERE LOWER(nome) = LOWER(:nome)")
            .param("nome", nome)
            .query(MAPEADOR_LINHA)
            .optional();
    }

    @Override
    public List<Connector> buscarTodos() {
        return clienteJdbc.sql(COLUNAS_SELECAO + " ORDER BY nome")
            .query(MAPEADOR_LINHA)
            .list();
    }
}
