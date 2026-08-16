package com.datarelay.connector.application;

import com.datarelay.connector.domain.Connector;
import com.datarelay.connector.domain.ConnectorRepository;
import com.datarelay.connector.infrastructure.JdbcConnectionFactory;
import com.datarelay.shared.domain.ConflictException;
import com.datarelay.shared.domain.NotFoundException;
import com.datarelay.plan.domain.ReplicationPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ConnectorService {

    private final ConnectorRepository repositorio;
    private final JdbcConnectionFactory fabricaConexoes;
    private final ReplicationPlanRepository repositorioPlanos;
    private final Clock relogio;

    public ConnectorService(ConnectorRepository repositorio, JdbcConnectionFactory fabricaConexoes,
                            ReplicationPlanRepository repositorioPlanos, Clock relogio) {
        this.repositorio = repositorio;
        this.fabricaConexoes = fabricaConexoes;
        this.repositorioPlanos = repositorioPlanos;
        this.relogio = relogio;
    }

    @Transactional
    public Connector criar(CreateConnectorCommand comando) {
        String nomeNormalizado = comando.nome() == null ? "" : comando.nome().trim();
        repositorio.buscarPorNome(nomeNormalizado).ifPresent(existente -> {
            throw new ConflictException("Ja existe um conector chamado '" + nomeNormalizado + "'");
        });

        Connector conector = Connector.criar(
            UUID.randomUUID(),
            nomeNormalizado,
            comando.papel(),
            comando.urlJdbc(),
            comando.usuario(),
            comando.referenciaSegredo(),
            relogio.instant());
        repositorio.salvar(conector);
        return conector;
    }

    @Transactional(readOnly = true)
    public Connector buscar(UUID id) {
        return repositorio.buscarPorId(id).orElseThrow(() -> new NotFoundException("Conector", id));
    }

    @Transactional(readOnly = true)
    public List<Connector> listar() {
        return repositorio.buscarTodos();
    }

    @Transactional
    public Connector atualizar(UUID id, UpdateConnectorCommand comando) {
        Connector atual = buscar(id);
        exigirForaDePlanoAtivo(id);
        String nome = comando.nome() == null ? "" : comando.nome().trim();
        repositorio.buscarPorNome(nome)
            .filter(encontrado -> !encontrado.id().equals(id))
            .ifPresent(encontrado -> {
                throw new ConflictException("Ja existe um conector chamado '" + nome + "'");
            });
        Connector atualizado = atual.atualizar(
            nome, comando.urlJdbc(), comando.usuario(), comando.referenciaSegredo(), relogio.instant());
        repositorio.atualizar(atualizado);
        return atualizado;
    }

    @Transactional
    public Connector alterarAtivacao(UUID id, boolean ativo) {
        Connector atual = buscar(id);
        if (!ativo) {
            exigirForaDePlanoAtivo(id);
        }
        Connector atualizado = atual.alterarAtivacao(ativo, relogio.instant());
        repositorio.atualizar(atualizado);
        return atualizado;
    }

    public ConnectionProbeResult testar(UUID id) {
        Connector conector = buscar(id);
        Instant iniciadoEm = relogio.instant();
        try (Connection conexao = fabricaConexoes.abrir(conector)) {
            boolean conexaoValida = conexao.isValid(2);
            long latencia = Duration.between(iniciadoEm, relogio.instant()).toMillis();
            return new ConnectionProbeResult(
                conexaoValida,
                latencia,
                conexaoValida ? "Conexao estabelecida" : "Conexao rejeitada");
        } catch (SQLException excecao) {
            long latencia = Duration.between(iniciadoEm, relogio.instant()).toMillis();
            return new ConnectionProbeResult(false, latencia, sanitizar(excecao.getMessage()));
        }
    }

    private String sanitizar(String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            return "Falha na conexao com o banco de dados";
        }
        return mensagem.length() <= 300 ? mensagem : mensagem.substring(0, 300);
    }

    private void exigirForaDePlanoAtivo(UUID conectorId) {
        if (repositorioPlanos.existePlanoAtivoComConector(conectorId)) {
            throw new ConflictException(
                "Desative os planos que utilizam o conector antes de altera-lo");
        }
    }
}
