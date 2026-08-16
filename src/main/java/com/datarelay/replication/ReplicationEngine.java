package com.datarelay.replication;

import com.datarelay.connector.domain.Connector;
import com.datarelay.connector.infrastructure.JdbcConnectionFactory;
import com.datarelay.execution.domain.CheckpointRepository;
import com.datarelay.execution.domain.ReplicationCheckpoint;
import com.datarelay.plan.domain.ReplicationMode;
import com.datarelay.plan.domain.ReplicationPlan;
import com.datarelay.plan.domain.TableMapping;
import com.datarelay.shared.domain.DomainException;
import com.datarelay.shared.domain.InfrastructureException;
import com.datarelay.shared.domain.SqlIdentifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ReplicationEngine {

    private static final Instant PONTO_CONTROLE_INICIAL = Instant.parse("1970-01-01T00:00:00Z");

    private final JdbcConnectionFactory fabricaConexoes;
    private final CheckpointRepository repositorioPontosControle;
    private final Clock relogio;
    private final Duration sobreposicaoIncremental;

    public ReplicationEngine(JdbcConnectionFactory fabricaConexoes,
                             CheckpointRepository repositorioPontosControle,
                             Clock relogio,
                             @Value("${datarelay.replicacao.sobreposicao:1s}") Duration sobreposicaoIncremental) {
        this.fabricaConexoes = fabricaConexoes;
        this.repositorioPontosControle = repositorioPontosControle;
        this.relogio = relogio;
        this.sobreposicaoIncremental = sobreposicaoIncremental;
    }

    public ReplicationResult replicar(ReplicationPlan plano, Connector origem, Connector destino,
                                       TableMapping mapeamento, ReplicationMode modo) {
        if (!mapeamento.ativo()) {
            return ReplicationResult.vazio();
        }
        if (modo == ReplicationMode.INCREMENTAL && mapeamento.colunaIncremental() == null) {
            throw new DomainException("A replicacao incremental exige uma coluna incremental");
        }

        try (Connection conexaoOrigem = fabricaConexoes.abrir(origem);
             Connection conexaoDestino = fabricaConexoes.abrir(destino)) {
            configurarOrigem(conexaoOrigem);
            conexaoDestino.setAutoCommit(false);
            ReplicationResult resultado = modo == ReplicationMode.COMPLETA
                ? replicarCompleta(plano, destino, mapeamento, conexaoOrigem, conexaoDestino)
                : replicarIncremental(plano, destino, mapeamento, conexaoOrigem, conexaoDestino);
            conexaoOrigem.commit();
            return resultado;
        } catch (SQLException excecao) {
            throw new InfrastructureException(
                "Falha ao replicar " + mapeamento.esquemaOrigem() + "." + mapeamento.tabelaOrigem()
                    + " -> " + destino.nome(),
                excecao);
        }
    }

    private ReplicationResult replicarCompleta(ReplicationPlan plano, Connector destino, TableMapping mapeamento,
                                            Connection conexaoOrigem, Connection conexaoDestino)
        throws SQLException {
        Long chaveMaxima = buscarMaiorChave(conexaoOrigem, mapeamento);
        if (chaveMaxima == null) {
            return ReplicationResult.vazio();
        }

        Long cursor = null;
        ReplicationResult total = ReplicationResult.vazio();
        while (cursor == null || cursor < chaveMaxima) {
            List<DadosLinha> linhas = lerLoteCompleto(
                conexaoOrigem, mapeamento, cursor, chaveMaxima, plano.tamanhoLote());
            if (linhas.isEmpty()) {
                break;
            }
            escreverLote(conexaoDestino, mapeamento, linhas);
            conexaoDestino.commit();
            cursor = linhas.getLast().chave();
            total = total.somar(new ReplicationResult(linhas.size(), linhas.size(), 1));
        }

        MarcaControle marcaSuperior = mapeamento.colunaIncremental() == null
            ? new MarcaControle(null, chaveMaxima)
            : buscarMaiorMarca(conexaoOrigem, mapeamento).orElse(new MarcaControle(null, chaveMaxima));
        salvarPontoControle(plano, destino, mapeamento, marcaSuperior);
        return total;
    }

    private ReplicationResult replicarIncremental(ReplicationPlan plano, Connector destino, TableMapping mapeamento,
                                                   Connection conexaoOrigem, Connection conexaoDestino)
        throws SQLException {
        Optional<MarcaControle> limiteSuperior = buscarMaiorMarca(conexaoOrigem, mapeamento);
        if (limiteSuperior.isEmpty()) {
            return ReplicationResult.vazio();
        }

        ReplicationCheckpoint salvo = repositorioPontosControle
            .buscar(plano.id(), destino.id(), mapeamento.id())
            .orElse(null);
        Instant instanteSalvo = salvo == null || salvo.ultimoValorIncremental() == null
            ? PONTO_CONTROLE_INICIAL : salvo.ultimoValorIncremental();
        Instant instanteInferior = subtrairComSeguranca(instanteSalvo, sobreposicaoIncremental);
        long chaveInferior = instanteInferior.equals(instanteSalvo) && salvo != null && salvo.ultimoValorChave() != null
            ? salvo.ultimoValorChave() : Long.MIN_VALUE;
        MarcaControle cursor = new MarcaControle(instanteInferior, chaveInferior);
        MarcaControle superior = limiteSuperior.get();
        if (comparar(cursor, superior) >= 0) {
            return ReplicationResult.vazio();
        }

        ReplicationResult total = ReplicationResult.vazio();
        while (comparar(cursor, superior) < 0) {
            List<DadosLinha> linhas = lerLoteIncremental(
                conexaoOrigem, mapeamento, cursor, superior, plano.tamanhoLote());
            if (linhas.isEmpty()) {
                break;
            }
            escreverLote(conexaoDestino, mapeamento, linhas);
            conexaoDestino.commit();
            DadosLinha ultimaLinha = linhas.getLast();
            cursor = new MarcaControle(ultimaLinha.valorIncremental(), ultimaLinha.chave());
            salvarPontoControle(plano, destino, mapeamento, cursor);
            total = total.somar(new ReplicationResult(linhas.size(), linhas.size(), 1));
        }
        return total;
    }

    private void configurarOrigem(Connection conexao) throws SQLException {
        conexao.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        conexao.setReadOnly(true);
        conexao.setAutoCommit(false);
    }

    private Long buscarMaiorChave(Connection conexao, TableMapping mapeamento) throws SQLException {
        String sql = "SELECT " + citar(mapeamento.colunaChave())
            + " FROM " + qualificado(mapeamento.esquemaOrigem(), mapeamento.tabelaOrigem())
            + " ORDER BY " + citar(mapeamento.colunaChave()) + " DESC LIMIT 1";
        try (PreparedStatement instrucao = conexao.prepareStatement(sql);
             ResultSet conjuntoResultados = instrucao.executeQuery()) {
            return conjuntoResultados.next() ? chaveNumerica(conjuntoResultados.getObject(1)) : null;
        }
    }

    private Optional<MarcaControle> buscarMaiorMarca(Connection conexao, TableMapping mapeamento)
        throws SQLException {
        String incremental = citar(mapeamento.colunaIncremental());
        String chave = citar(mapeamento.colunaChave());
        String sql = "SELECT " + incremental + ", " + chave
            + " FROM " + qualificado(mapeamento.esquemaOrigem(), mapeamento.tabelaOrigem())
            + " WHERE " + incremental + " IS NOT NULL"
            + " ORDER BY " + incremental + " DESC, " + chave + " DESC LIMIT 1";
        try (PreparedStatement instrucao = conexao.prepareStatement(sql);
             ResultSet conjuntoResultados = instrucao.executeQuery()) {
            if (!conjuntoResultados.next()) {
                return Optional.empty();
            }
            return Optional.of(new MarcaControle(
                paraInstante(conjuntoResultados.getObject(1)),
                chaveNumerica(conjuntoResultados.getObject(2))));
        }
    }

    private List<DadosLinha> lerLoteCompleto(Connection conexao, TableMapping mapeamento, Long cursor,
                                        long chaveMaxima, int tamanhoLote) throws SQLException {
        String chave = citar(mapeamento.colunaChave());
        StringBuilder sql = new StringBuilder("SELECT ")
            .append(listaColunas(mapeamento.colunas()))
            .append(" FROM ").append(qualificado(mapeamento.esquemaOrigem(), mapeamento.tabelaOrigem()))
            .append(" WHERE ").append(chave).append(" <= ?");
        if (cursor != null) {
            sql.append(" AND ").append(chave).append(" > ?");
        }
        sql.append(" ORDER BY ").append(chave).append(" LIMIT ?");

        try (PreparedStatement instrucao = conexao.prepareStatement(sql.toString())) {
            int parametro = 1;
            instrucao.setLong(parametro++, chaveMaxima);
            if (cursor != null) {
                instrucao.setLong(parametro++, cursor);
            }
            instrucao.setInt(parametro, tamanhoLote);
            instrucao.setFetchSize(tamanhoLote);
            return lerLinhas(instrucao, mapeamento);
        }
    }

    private List<DadosLinha> lerLoteIncremental(Connection conexao, TableMapping mapeamento,
                                               MarcaControle cursor, MarcaControle superior, int tamanhoLote)
        throws SQLException {
        String incremental = citar(mapeamento.colunaIncremental());
        String chave = citar(mapeamento.colunaChave());
        String sql = "SELECT " + listaColunas(mapeamento.colunas())
            + " FROM " + qualificado(mapeamento.esquemaOrigem(), mapeamento.tabelaOrigem())
            + " WHERE " + incremental + " IS NOT NULL"
            + " AND (" + incremental + " > ? OR (" + incremental + " = ? AND " + chave + " > ?))"
            + " AND (" + incremental + " < ? OR (" + incremental + " = ? AND " + chave + " <= ?))"
            + " ORDER BY " + incremental + ", " + chave + " LIMIT ?";

        try (PreparedStatement instrucao = conexao.prepareStatement(sql)) {
            Timestamp limiteInferior = Timestamp.from(cursor.valorIncremental());
            Timestamp limiteSuperior = Timestamp.from(superior.valorIncremental());
            instrucao.setTimestamp(1, limiteInferior);
            instrucao.setTimestamp(2, limiteInferior);
            instrucao.setLong(3, cursor.chave());
            instrucao.setTimestamp(4, limiteSuperior);
            instrucao.setTimestamp(5, limiteSuperior);
            instrucao.setLong(6, superior.chave());
            instrucao.setInt(7, tamanhoLote);
            instrucao.setFetchSize(tamanhoLote);
            return lerLinhas(instrucao, mapeamento);
        }
    }

    private List<DadosLinha> lerLinhas(PreparedStatement instrucao, TableMapping mapeamento) throws SQLException {
        int indiceChave = mapeamento.colunas().indexOf(mapeamento.colunaChave()) + 1;
        int indiceIncremental = mapeamento.colunaIncremental() == null
            ? -1 : mapeamento.colunas().indexOf(mapeamento.colunaIncremental()) + 1;
        List<DadosLinha> linhas = new ArrayList<>();
        try (ResultSet conjuntoResultados = instrucao.executeQuery()) {
            while (conjuntoResultados.next()) {
                Object[] valores = new Object[mapeamento.colunas().size()];
                for (int indice = 0; indice < valores.length; indice++) {
                    valores[indice] = conjuntoResultados.getObject(indice + 1);
                }
                long chave = chaveNumerica(conjuntoResultados.getObject(indiceChave));
                Instant valorIncremental = indiceIncremental < 0
                    ? null : paraInstante(conjuntoResultados.getObject(indiceIncremental));
                linhas.add(new DadosLinha(valores, chave, valorIncremental));
            }
        }
        return linhas;
    }

    private void escreverLote(Connection conexao, TableMapping mapeamento, List<DadosLinha> linhas) throws SQLException {
        String colunas = listaColunas(mapeamento.colunas());
        String marcadores = String.join(", ", mapeamento.colunas().stream().map(coluna -> "?").toList());
        List<String> atualizaveis = mapeamento.colunas().stream()
            .filter(coluna -> !coluna.equals(mapeamento.colunaChave()))
            .toList();
        String acaoConflito = atualizaveis.isEmpty()
            ? "DO NOTHING"
            : "DO UPDATE SET " + String.join(", ", atualizaveis.stream()
                .map(coluna -> citar(coluna) + " = EXCLUDED." + citar(coluna))
                .toList());
        String sql = "INSERT INTO " + qualificado(mapeamento.esquemaDestino(), mapeamento.tabelaDestino())
            + " (" + colunas + ") VALUES (" + marcadores + ")"
            + " ON CONFLICT (" + citar(mapeamento.colunaChave()) + ") " + acaoConflito;

        try (PreparedStatement instrucao = conexao.prepareStatement(sql)) {
            for (DadosLinha linha : linhas) {
                for (int indice = 0; indice < linha.valores().length; indice++) {
                    instrucao.setObject(indice + 1, linha.valores()[indice]);
                }
                instrucao.addBatch();
            }
            instrucao.executeBatch();
        } catch (SQLException excecao) {
            conexao.rollback();
            throw excecao;
        }
    }

    private void salvarPontoControle(ReplicationPlan plano, Connector destino, TableMapping mapeamento,
                                     MarcaControle marcaControle) {
        repositorioPontosControle.salvar(new ReplicationCheckpoint(
            plano.id(),
            destino.id(),
            mapeamento.id(),
            marcaControle.valorIncremental(),
            marcaControle.chave(),
            relogio.instant()));
    }

    private String qualificado(String esquema, String tabela) {
        return citar(esquema) + "." + citar(tabela);
    }

    private String listaColunas(List<String> colunas) {
        return String.join(", ", colunas.stream().map(this::citar).toList());
    }

    private String citar(String identificador) {
        return SqlIdentifier.citar(identificador);
    }

    private long chaveNumerica(Object valor) {
        if (!(valor instanceof Number numero)) {
            throw new DomainException("A chave de replicacao deve ser um valor numerico nao nulo");
        }
        return numero.longValue();
    }

    private Instant paraInstante(Object valor) {
        return switch (valor) {
            case Instant instante -> instante;
            case Timestamp dataHora -> dataHora.toInstant();
            case OffsetDateTime dataHoraComFuso -> dataHoraComFuso.toInstant();
            case LocalDateTime dataHoraLocal -> dataHoraLocal.toInstant(ZoneOffset.UTC);
            case null -> throw new DomainException("A coluna incremental nao pode ser nula");
            default -> throw new DomainException("A coluna incremental deve conter uma data e hora");
        };
    }

    private Instant subtrairComSeguranca(Instant valor, Duration duracao) {
        try {
            return valor.minus(duracao);
        } catch (DateTimeException | ArithmeticException excecao) {
            return Instant.MIN;
        }
    }

    private int comparar(MarcaControle esquerda, MarcaControle direita) {
        int comparacaoInstantes = esquerda.valorIncremental().compareTo(direita.valorIncremental());
        return comparacaoInstantes != 0
            ? comparacaoInstantes
            : Long.compare(esquerda.chave(), direita.chave());
    }

    private record DadosLinha(Object[] valores, long chave, Instant valorIncremental) {
    }

    private record MarcaControle(Instant valorIncremental, long chave) {
    }
}
