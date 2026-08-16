package com.datarelay.replication;

import com.datarelay.connector.application.ConnectorService;
import com.datarelay.connector.application.CreateConnectorCommand;
import com.datarelay.connector.domain.Connector;
import com.datarelay.connector.domain.ConnectorRole;
import com.datarelay.connector.infrastructure.SecretProvider;
import com.datarelay.execution.application.ReplicationExecutionService;
import com.datarelay.execution.domain.ReplicationRun;
import com.datarelay.execution.domain.CheckpointRepository;
import com.datarelay.execution.domain.ReplicationCheckpoint;
import com.datarelay.execution.domain.RunStatus;
import com.datarelay.execution.domain.TargetRunStatus;
import com.datarelay.plan.application.CreateReplicationPlanCommand;
import com.datarelay.plan.application.CreateTableMappingCommand;
import com.datarelay.plan.application.ReplicationPlanService;
import com.datarelay.plan.domain.ReplicationMode;
import com.datarelay.plan.domain.ReplicationPlan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(properties = {
    "datarelay.agendador.ativo=false",
    "datarelay.recuperacao.ativa=false",
    "datarelay.replicacao.sobreposicao=1s"
})
@Import(DataRelayEndToEndIT.ConfiguracaoSegredoTeste.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DataRelayEndToEndIT {

    private static final String SENHA = "segredo-teste";

    @Container
    static final PostgreSQLContainer<?> CONTROLE = postgres("controle");

    @Container
    static final PostgreSQLContainer<?> ORIGEM = postgres("origem");

    @Container
    static final PostgreSQLContainer<?> DESTINO_UM = postgres("destino_um");

    @Container
    static final PostgreSQLContainer<?> DESTINO_DOIS = postgres("destino_dois");

    @DynamicPropertySource
    static void configurarBancoControle(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", CONTROLE::getJdbcUrl);
        registro.add("spring.datasource.username", CONTROLE::getUsername);
        registro.add("spring.datasource.password", CONTROLE::getPassword);
    }

    @Autowired
    ConnectorService servicoConectores;

    @Autowired
    ReplicationPlanService servicoPlanos;

    @Autowired
    ReplicationExecutionService servicoExecucoes;

    @Autowired
    CheckpointRepository repositorioPontosControle;

    @Test
    void replicaDadosCompletosEIncrementaisComIdempotenciaEIsolaFalhaDeDestino() throws Exception {
        criarEsquemaOrigem();
        criarEsquemaDestino(DESTINO_UM);
        criarEsquemaDestino(DESTINO_DOIS);

        Connector origem = criarConector("origem", ConnectorRole.ORIGEM, ORIGEM);
        Connector destinoUm = criarConector("destino-um", ConnectorRole.DESTINO, DESTINO_UM);
        Connector destinoDois = criarConector("destino-dois", ConnectorRole.DESTINO, DESTINO_DOIS);
        assertThatThrownBy(() -> criarPlanoComDependenciaInvertida(origem, destinoUm))
            .hasMessageContaining("precisa aparecer antes no plano");
        ReplicationPlan plano = criarPlano(origem, destinoUm, destinoDois);

        ReplicationRun completa = aguardar(servicoExecucoes.iniciarManual(
            plano.id(), "carga-completa-inicial", ReplicationMode.COMPLETA).id());
        assertThat(completa.status()).isEqualTo(RunStatus.CONCLUIDA);
        assertThat(completa.linhasEscritas()).isEqualTo(8);
        assertThat(contarClientes(DESTINO_UM)).isEqualTo(2);
        assertThat(contarClientes(DESTINO_DOIS)).isEqualTo(2);
        assertThat(contarPedidos(DESTINO_UM)).isEqualTo(2);
        assertThat(contarPedidos(DESTINO_DOIS)).isEqualTo(2);

        executar(ORIGEM, """
            UPDATE clientes
            SET email = 'ana.atualizada@example.com', atualizado_em = '2026-02-01T10:00:00Z'
            WHERE id = 1;
            INSERT INTO clientes (id, nome, email, atualizado_em)
            VALUES (3, 'Carla Nunes', 'carla@example.com', '2026-02-01T10:00:00Z');
            UPDATE pedidos
            SET status = 'ENVIADO', atualizado_em = '2026-02-01T10:01:00Z'
            WHERE id = 1;
            INSERT INTO pedidos (id, cliente_id, total, status, atualizado_em)
            VALUES (3, 3, 159.90, 'PAGO', '2026-02-01T10:02:00Z');
            """);

        ReplicationRun incremental = aguardar(servicoExecucoes.iniciarManual(
            plano.id(), "incremental-fevereiro", ReplicationMode.INCREMENTAL).id());
        assertThat(incremental.status()).isEqualTo(RunStatus.CONCLUIDA);
        assertThat(contarClientes(DESTINO_UM)).isEqualTo(3);
        assertThat(emailCliente(DESTINO_UM, 1)).isEqualTo("ana.atualizada@example.com");
        assertThat(contarPedidos(DESTINO_UM)).isEqualTo(3);
        assertThat(statusPedido(DESTINO_UM, 1)).isEqualTo("ENVIADO");

        var mapeamentoClientes = plano.mapeamentos().getFirst();
        ReplicationCheckpoint pontoAntes = repositorioPontosControle
            .buscar(plano.id(), destinoUm.id(), mapeamentoClientes.id()).orElseThrow();
        repositorioPontosControle.salvar(new ReplicationCheckpoint(
            plano.id(), destinoUm.id(), mapeamentoClientes.id(),
            Instant.parse("2000-01-01T00:00:00Z"), 1L, Instant.now()));
        ReplicationCheckpoint pontoDepois = repositorioPontosControle
            .buscar(plano.id(), destinoUm.id(), mapeamentoClientes.id()).orElseThrow();
        assertThat(pontoDepois.ultimoValorIncremental()).isEqualTo(pontoAntes.ultimoValorIncremental());
        assertThat(pontoDepois.ultimoValorChave()).isEqualTo(pontoAntes.ultimoValorChave());

        ReplicationRun duplicada = servicoExecucoes.iniciarManual(
            plano.id(), "incremental-fevereiro", ReplicationMode.INCREMENTAL);
        assertThat(duplicada.id()).isEqualTo(incremental.id());
        assertThat(contarClientes(DESTINO_UM)).isEqualTo(3);

        executar(DESTINO_DOIS, "DROP TABLE clientes CASCADE");
        executar(ORIGEM, """
            UPDATE clientes
            SET email = 'bruno.atualizado@example.com', atualizado_em = '2026-03-01T10:00:00Z'
            WHERE id = 2;
            UPDATE pedidos
            SET status = 'CANCELADO', atualizado_em = '2026-03-01T10:01:00Z'
            WHERE id = 2;
            """);

        ReplicationRun parcial = aguardar(servicoExecucoes.iniciarManual(
            plano.id(), "incremental-marco", ReplicationMode.INCREMENTAL).id());
        assertThat(parcial.status()).isEqualTo(RunStatus.PARCIALMENTE_CONCLUIDA);
        assertThat(emailCliente(DESTINO_UM, 2)).isEqualTo("bruno.atualizado@example.com");
        assertThat(parcial.destinos())
            .extracting(destino -> destino.status())
            .containsExactlyInAnyOrder(TargetRunStatus.CONCLUIDA, TargetRunStatus.FALHOU);

        recriarTabelaClientes(DESTINO_DOIS);
        ReplicationRun reprocessamento = aguardar(servicoExecucoes.reprocessarDestino(
            parcial.id(), destinoDois.id(), "reprocessar-destino-dois", ReplicationMode.COMPLETA).id());
        assertThat(reprocessamento.status()).isEqualTo(RunStatus.CONCLUIDA);
        assertThat(reprocessamento.execucaoOrigemId()).isEqualTo(parcial.id());
        assertThat(reprocessamento.conectorDestinoRestritoId()).isEqualTo(destinoDois.id());
        assertThat(reprocessamento.destinos()).hasSize(1);
        assertThat(contarClientes(DESTINO_DOIS)).isEqualTo(3);
        assertThat(contarPedidos(DESTINO_DOIS)).isEqualTo(3);
        assertThat(emailCliente(DESTINO_DOIS, 2)).isEqualTo("bruno.atualizado@example.com");
        assertThat(statusPedido(DESTINO_DOIS, 2)).isEqualTo("CANCELADO");

        executar(DESTINO_DOIS, """
            ALTER TABLE pedidos
            ADD CONSTRAINT fk_pedidos_clientes FOREIGN KEY (cliente_id) REFERENCES clientes(id)
            """);
    }

    private Connector criarConector(String nome, ConnectorRole papel, PostgreSQLContainer<?> banco) {
        return servicoConectores.criar(new CreateConnectorCommand(
            nome + "-" + UUID.randomUUID(),
            papel,
            banco.getJdbcUrl(),
            banco.getUsername(),
            "env:SENHA_BANCO_TESTE"));
    }

    private ReplicationPlan criarPlano(Connector origem, Connector destinoUm, Connector destinoDois) {
        return servicoPlanos.criar(new CreateReplicationPlanCommand(
            "plano-clientes-" + UUID.randomUUID(),
            origem.id(),
            List.of(destinoUm.id(), destinoDois.id()),
            ReplicationMode.INCREMENTAL,
            1,
            null,
            List.of(
                new CreateTableMappingCommand(
                    "public", "clientes", "public", "clientes", "id", "atualizado_em",
                    List.of("id", "nome", "email", "atualizado_em")),
                new CreateTableMappingCommand(
                    "public", "pedidos", "public", "pedidos", "id", "atualizado_em",
                    List.of("id", "cliente_id", "total", "status", "atualizado_em")))));
    }

    private void criarEsquemaOrigem() throws SQLException {
        executar(ORIGEM, """
            CREATE TABLE clientes (
                id BIGINT PRIMARY KEY,
                nome VARCHAR(120) NOT NULL,
                email VARCHAR(180) NOT NULL UNIQUE,
                atualizado_em TIMESTAMPTZ NOT NULL
            );
            CREATE TABLE pedidos (
                id BIGINT PRIMARY KEY,
                cliente_id BIGINT NOT NULL REFERENCES clientes(id),
                total NUMERIC(12, 2) NOT NULL CHECK (total >= 0),
                status VARCHAR(30) NOT NULL,
                atualizado_em TIMESTAMPTZ NOT NULL
            );
            INSERT INTO clientes (id, nome, email, atualizado_em) VALUES
                (1, 'Ana Souza', 'ana@example.com', '2026-01-01T10:00:00Z'),
                (2, 'Bruno Lima', 'bruno@example.com', '2026-01-01T10:01:00Z');
            INSERT INTO pedidos (id, cliente_id, total, status, atualizado_em) VALUES
                (1, 1, 249.90, 'PAGO', '2026-01-01T10:02:00Z'),
                (2, 2, 89.50, 'PENDENTE', '2026-01-01T10:03:00Z');
            """);
    }

    private void criarEsquemaDestino(PostgreSQLContainer<?> banco) throws SQLException {
        executar(banco, """
            CREATE TABLE clientes (
                id BIGINT PRIMARY KEY,
                nome VARCHAR(120) NOT NULL,
                email VARCHAR(180) NOT NULL UNIQUE,
                atualizado_em TIMESTAMPTZ NOT NULL
            );
            CREATE TABLE pedidos (
                id BIGINT PRIMARY KEY,
                cliente_id BIGINT NOT NULL REFERENCES clientes(id),
                total NUMERIC(12, 2) NOT NULL CHECK (total >= 0),
                status VARCHAR(30) NOT NULL,
                atualizado_em TIMESTAMPTZ NOT NULL
            )
            """);
    }

    private ReplicationPlan criarPlanoComDependenciaInvertida(Connector origem, Connector destino) {
        return servicoPlanos.criar(new CreateReplicationPlanCommand(
            "plano-invalido-" + UUID.randomUUID(),
            origem.id(),
            List.of(destino.id()),
            ReplicationMode.INCREMENTAL,
            10,
            null,
            List.of(
                new CreateTableMappingCommand(
                    "public", "pedidos", "public", "pedidos", "id", "atualizado_em",
                    List.of("id", "cliente_id", "total", "status", "atualizado_em")),
                new CreateTableMappingCommand(
                    "public", "clientes", "public", "clientes", "id", "atualizado_em",
                    List.of("id", "nome", "email", "atualizado_em")))));
    }

    private void recriarTabelaClientes(PostgreSQLContainer<?> banco) throws SQLException {
        executar(banco, """
            CREATE TABLE clientes (
                id BIGINT PRIMARY KEY,
                nome VARCHAR(120) NOT NULL,
                email VARCHAR(180) NOT NULL UNIQUE,
                atualizado_em TIMESTAMPTZ NOT NULL
            )
            """);
    }

    private ReplicationRun aguardar(UUID execucaoId) throws InterruptedException {
        Instant prazo = Instant.now().plus(Duration.ofSeconds(30));
        ReplicationRun execucao;
        do {
            execucao = servicoExecucoes.buscar(execucaoId);
            if (execucao.status() != RunStatus.NA_FILA && execucao.status() != RunStatus.EM_EXECUCAO) {
                return execucao;
            }
            Thread.sleep(100);
        } while (Instant.now().isBefore(prazo));
        throw new AssertionError("A execucao de replicacao nao terminou: " + execucaoId);
    }

    private long contarClientes(PostgreSQLContainer<?> banco) throws SQLException {
        try (Connection conexao = conectar(banco);
             Statement instrucao = conexao.createStatement();
             ResultSet conjuntoResultados = instrucao.executeQuery("SELECT COUNT(*) FROM clientes")) {
            conjuntoResultados.next();
            return conjuntoResultados.getLong(1);
        }
    }

    private long contarPedidos(PostgreSQLContainer<?> banco) throws SQLException {
        try (Connection conexao = conectar(banco);
             Statement instrucao = conexao.createStatement();
             ResultSet conjuntoResultados = instrucao.executeQuery("SELECT COUNT(*) FROM pedidos")) {
            conjuntoResultados.next();
            return conjuntoResultados.getLong(1);
        }
    }

    private String emailCliente(PostgreSQLContainer<?> banco, long id) throws SQLException {
        try (Connection conexao = conectar(banco);
             var instrucao = conexao.prepareStatement("SELECT email FROM clientes WHERE id = ?")) {
            instrucao.setLong(1, id);
            try (ResultSet conjuntoResultados = instrucao.executeQuery()) {
                conjuntoResultados.next();
                return conjuntoResultados.getString(1);
            }
        }
    }

    private String statusPedido(PostgreSQLContainer<?> banco, long id) throws SQLException {
        try (Connection conexao = conectar(banco);
             var instrucao = conexao.prepareStatement("SELECT status FROM pedidos WHERE id = ?")) {
            instrucao.setLong(1, id);
            try (ResultSet conjuntoResultados = instrucao.executeQuery()) {
                conjuntoResultados.next();
                return conjuntoResultados.getString(1);
            }
        }
    }

    private static void executar(PostgreSQLContainer<?> banco, String sql) throws SQLException {
        try (Connection conexao = conectar(banco); Statement instrucao = conexao.createStatement()) {
            instrucao.execute(sql);
        }
    }

    private static Connection conectar(PostgreSQLContainer<?> banco) throws SQLException {
        return DriverManager.getConnection(banco.getJdbcUrl(), banco.getUsername(), banco.getPassword());
    }

    private static PostgreSQLContainer<?> postgres(String nomeBanco) {
        return new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName(nomeBanco)
            .withUsername("teste")
            .withPassword(SENHA);
    }

    @TestConfiguration
    static class ConfiguracaoSegredoTeste {

        @Bean
        @Primary
        SecretProvider provedorSegredoTeste() {
            return referencia -> SENHA;
        }
    }
}
