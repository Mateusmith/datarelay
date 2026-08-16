package com.datarelay.replication;

import com.datarelay.connector.domain.Connector;
import com.datarelay.connector.infrastructure.JdbcConnectionFactory;
import com.datarelay.plan.domain.ReplicationPlan;
import com.datarelay.plan.domain.TableMapping;
import com.datarelay.shared.domain.DomainException;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ReplicationSchemaValidator {

    private static final Set<Integer> TIPOS_CHAVE_NUMERICA = Set.of(
        Types.BIGINT, Types.INTEGER, Types.SMALLINT);
    private static final Set<Integer> TIPOS_DATA_HORA = Set.of(
        Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE);

    private final JdbcConnectionFactory fabricaConexoes;

    public ReplicationSchemaValidator(JdbcConnectionFactory fabricaConexoes) {
        this.fabricaConexoes = fabricaConexoes;
    }

    public SchemaValidationResult validar(ReplicationPlan plano, Connector origem, List<Connector> destinos) {
        List<String> erros = new ArrayList<>();
        Map<String, MetadataTable> tabelasOrigem = new HashMap<>();

        try (Connection conexaoOrigem = fabricaConexoes.abrir(origem)) {
            DatabaseMetaData metadados = conexaoOrigem.getMetaData();
            for (TableMapping mapeamento : plano.mapeamentos()) {
                MetadataTable tabela = carregarTabela(
                    metadados, mapeamento.esquemaOrigem(), mapeamento.tabelaOrigem());
                tabelasOrigem.put(chaveTabela(mapeamento.esquemaOrigem(), mapeamento.tabelaOrigem()), tabela);
                validarOrigem(mapeamento, tabela, origem.nome(), erros);
            }
        } catch (SQLException excecao) {
            erros.add("Nao foi possivel inspecionar a origem '" + origem.nome() + "': " + mensagem(excecao));
        }

        for (Connector destino : destinos) {
            try (Connection conexaoDestino = fabricaConexoes.abrir(destino)) {
                DatabaseMetaData metadados = conexaoDestino.getMetaData();
                for (TableMapping mapeamento : plano.mapeamentos()) {
                    MetadataTable tabelaDestino = carregarTabela(
                        metadados, mapeamento.esquemaDestino(), mapeamento.tabelaDestino());
                    MetadataTable tabelaOrigem = tabelasOrigem.get(
                        chaveTabela(mapeamento.esquemaOrigem(), mapeamento.tabelaOrigem()));
                    validarDestino(mapeamento, tabelaOrigem, tabelaDestino, destino.nome(), erros);
                    validarOrdemDependencias(metadados, plano, mapeamento, destino.nome(), erros);
                }
            } catch (SQLException excecao) {
                erros.add("Nao foi possivel inspecionar o destino '" + destino.nome() + "': " + mensagem(excecao));
            }
        }

        return erros.isEmpty() ? SchemaValidationResult.sucesso() : SchemaValidationResult.falha(erros);
    }

    public void exigirValido(ReplicationPlan plano, Connector origem, List<Connector> destinos) {
        SchemaValidationResult resultado = validar(plano, origem, destinos);
        if (!resultado.valido()) {
            throw new DomainException("Esquema de replicacao invalido: " + String.join(" | ", resultado.erros()));
        }
    }

    private void validarOrigem(TableMapping mapeamento, MetadataTable tabela, String nomeConector,
                               List<String> erros) {
        String contexto = "origem '" + nomeConector + "' em "
            + mapeamento.esquemaOrigem() + "." + mapeamento.tabelaOrigem();
        if (!tabela.existe()) {
            erros.add("Tabela ausente na " + contexto);
            return;
        }
        validarColunas(mapeamento.colunas(), tabela, contexto, erros);
        ColumnMetadata chave = tabela.colunas().get(mapeamento.colunaChave());
        if (chave != null && !TIPOS_CHAVE_NUMERICA.contains(chave.tipoJdbc())) {
            erros.add("A coluna-chave " + mapeamento.colunaChave() + " da " + contexto
                + " deve ser SMALLINT, INTEGER ou BIGINT");
        }
        if (mapeamento.colunaIncremental() != null) {
            ColumnMetadata incremental = tabela.colunas().get(mapeamento.colunaIncremental());
            if (incremental != null && !TIPOS_DATA_HORA.contains(incremental.tipoJdbc())) {
                erros.add("A coluna incremental " + mapeamento.colunaIncremental() + " da " + contexto
                    + " deve ser TIMESTAMP ou TIMESTAMPTZ");
            }
            if (incremental != null && incremental.aceitaNulo()) {
                erros.add("A coluna incremental " + mapeamento.colunaIncremental() + " da " + contexto
                    + " deve ser NOT NULL");
            }
        }
    }

    private void validarDestino(TableMapping mapeamento, MetadataTable origem, MetadataTable destino,
                                String nomeConector, List<String> erros) {
        String contexto = "destino '" + nomeConector + "' em "
            + mapeamento.esquemaDestino() + "." + mapeamento.tabelaDestino();
        if (!destino.existe()) {
            erros.add("Tabela ausente no " + contexto);
            return;
        }
        validarColunas(mapeamento.colunas(), destino, contexto, erros);
        if (!destino.chavesPrimarias().contains(mapeamento.colunaChave())) {
            erros.add("A coluna-chave " + mapeamento.colunaChave() + " deve ser chave primaria no " + contexto);
        }
        if (origem == null || !origem.existe()) {
            return;
        }
        for (String coluna : mapeamento.colunas()) {
            ColumnMetadata colunaOrigem = origem.colunas().get(coluna);
            ColumnMetadata colunaDestino = destino.colunas().get(coluna);
            if (colunaOrigem != null && colunaDestino != null
                && !familiaTipo(colunaOrigem.tipoJdbc()).equals(familiaTipo(colunaDestino.tipoJdbc()))) {
                erros.add("Tipos incompativeis para a coluna " + coluna + " no " + contexto
                    + ": origem=" + colunaOrigem.nomeTipo() + ", destino=" + colunaDestino.nomeTipo());
            }
        }
    }

    private void validarColunas(List<String> colunas, MetadataTable tabela, String contexto, List<String> erros) {
        for (String coluna : colunas) {
            if (!tabela.colunas().containsKey(coluna)) {
                erros.add("Coluna ausente na " + contexto + ": " + coluna);
            }
        }
    }

    private void validarOrdemDependencias(DatabaseMetaData metadados, ReplicationPlan plano,
                                           TableMapping atual, String nomeDestino, List<String> erros)
        throws SQLException {
        Map<String, Integer> ordens = new HashMap<>();
        for (TableMapping mapeamento : plano.mapeamentos()) {
            ordens.put(chaveTabela(mapeamento.esquemaDestino(), mapeamento.tabelaDestino()), mapeamento.ordem());
        }
        try (ResultSet chavesEstrangeiras = metadados.getImportedKeys(
            null, atual.esquemaDestino(), atual.tabelaDestino())) {
            while (chavesEstrangeiras.next()) {
                String esquemaReferenciado = chavesEstrangeiras.getString("PKTABLE_SCHEM");
                String tabelaReferenciada = chavesEstrangeiras.getString("PKTABLE_NAME");
                Integer ordemReferenciada = ordens.get(chaveTabela(esquemaReferenciado, tabelaReferenciada));
                if (ordemReferenciada != null && ordemReferenciada >= atual.ordem()) {
                    erros.add("No destino '" + nomeDestino + "', a tabela " + atual.tabelaDestino()
                        + " depende de " + tabelaReferenciada + ", que precisa aparecer antes no plano");
                }
            }
        }
    }

    private MetadataTable carregarTabela(DatabaseMetaData metadados, String esquema, String tabela)
        throws SQLException {
        boolean existe;
        try (ResultSet tabelas = metadados.getTables(null, esquema, tabela, new String[]{"TABLE"})) {
            existe = tabelas.next();
        }
        if (!existe) {
            return MetadataTable.ausente();
        }

        Map<String, ColumnMetadata> colunas = new HashMap<>();
        try (ResultSet resultado = metadados.getColumns(null, esquema, tabela, null)) {
            while (resultado.next()) {
                colunas.put(resultado.getString("COLUMN_NAME"), new ColumnMetadata(
                    resultado.getInt("DATA_TYPE"),
                    resultado.getString("TYPE_NAME"),
                    resultado.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls));
            }
        }
        Set<String> chavesPrimarias = new HashSet<>();
        try (ResultSet resultado = metadados.getPrimaryKeys(null, esquema, tabela)) {
            while (resultado.next()) {
                chavesPrimarias.add(resultado.getString("COLUMN_NAME"));
            }
        }
        return new MetadataTable(true, Map.copyOf(colunas), Set.copyOf(chavesPrimarias));
    }

    private String familiaTipo(int tipoJdbc) {
        if (Set.of(Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT, Types.NUMERIC, Types.DECIMAL,
            Types.REAL, Types.FLOAT, Types.DOUBLE).contains(tipoJdbc)) {
            return "numero";
        }
        if (Set.of(Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR,
            Types.LONGNVARCHAR).contains(tipoJdbc)) {
            return "texto";
        }
        if (Set.of(Types.DATE, Types.TIME, Types.TIME_WITH_TIMEZONE, Types.TIMESTAMP,
            Types.TIMESTAMP_WITH_TIMEZONE).contains(tipoJdbc)) {
            return "data_hora";
        }
        if (Set.of(Types.BOOLEAN, Types.BIT).contains(tipoJdbc)) {
            return "booleano";
        }
        return "tipo_" + tipoJdbc;
    }

    private String chaveTabela(String esquema, String tabela) {
        return esquema + "." + tabela;
    }

    private String mensagem(SQLException excecao) {
        String mensagem = excecao.getMessage();
        return mensagem == null || mensagem.isBlank() ? excecao.getClass().getSimpleName() : mensagem;
    }

    private record MetadataTable(boolean existe, Map<String, ColumnMetadata> colunas,
                                 Set<String> chavesPrimarias) {
        private static MetadataTable ausente() {
            return new MetadataTable(false, Map.of(), Set.of());
        }
    }

    private record ColumnMetadata(int tipoJdbc, String nomeTipo, boolean aceitaNulo) {
    }
}
