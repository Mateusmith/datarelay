package com.datarelay.plan.application;

import com.datarelay.connector.domain.Connector;
import com.datarelay.connector.domain.ConnectorRepository;
import com.datarelay.connector.domain.ConnectorRole;
import com.datarelay.plan.domain.ReplicationPlan;
import com.datarelay.plan.domain.ReplicationPlanRepository;
import com.datarelay.plan.domain.TableMapping;
import com.datarelay.replication.ReplicationSchemaValidator;
import com.datarelay.replication.SchemaValidationResult;
import com.datarelay.execution.domain.ReplicationRunRepository;
import com.datarelay.shared.domain.ConflictException;
import com.datarelay.shared.domain.DomainException;
import com.datarelay.shared.domain.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class ReplicationPlanService {

    private final ReplicationPlanRepository repositorioPlanos;
    private final ConnectorRepository repositorioConectores;
    private final ScheduleCalculator calculadoraAgendamento;
    private final ReplicationSchemaValidator validadorEsquema;
    private final ReplicationRunRepository repositorioExecucoes;
    private final Clock relogio;

    public ReplicationPlanService(ReplicationPlanRepository repositorioPlanos,
                                  ConnectorRepository repositorioConectores,
                                  ScheduleCalculator calculadoraAgendamento,
                                  ReplicationSchemaValidator validadorEsquema,
                                  ReplicationRunRepository repositorioExecucoes,
                                  Clock relogio) {
        this.repositorioPlanos = repositorioPlanos;
        this.repositorioConectores = repositorioConectores;
        this.calculadoraAgendamento = calculadoraAgendamento;
        this.validadorEsquema = validadorEsquema;
        this.repositorioExecucoes = repositorioExecucoes;
        this.relogio = relogio;
    }

    @Transactional
    public ReplicationPlan criar(CreateReplicationPlanCommand comando) {
        String nome = comando.nome() == null ? "" : comando.nome().trim();
        repositorioPlanos.buscarPorNome(nome).ifPresent(existente -> {
            throw new ConflictException("Ja existe um plano de replicacao chamado '" + nome + "'");
        });

        Connector origem = exigirConector(comando.conectorOrigemId());
        if (origem.papel() != ConnectorRole.ORIGEM) {
            throw new DomainException("A origem do plano deve referenciar um conector ORIGEM");
        }
        List<UUID> idsDestinos = comando.idsConectoresDestino() == null
            ? List.of()
            : comando.idsConectoresDestino().stream().distinct().toList();
        List<Connector> destinos = idsDestinos.stream().map(this::exigirConector).toList();
        for (Connector destino : destinos) {
            if (destino.papel() != ConnectorRole.DESTINO) {
                throw new DomainException("Os destinos do plano devem referenciar conectores DESTINO: " + destino.id());
            }
        }

        List<CreateTableMappingCommand> comandosMapeamento =
            comando.mapeamentos() == null ? List.of() : comando.mapeamentos();
        List<TableMapping> mapeamentos = IntStream.range(0, comandosMapeamento.size())
            .mapToObj(indice -> paraMapeamento(indice, comandosMapeamento.get(indice)))
            .toList();
        Instant agora = relogio.instant();
        String cron = normalizarCron(comando.expressaoCron());
        ReplicationPlan plano = new ReplicationPlan(
            UUID.randomUUID(),
            nome,
            origem.id(),
            idsDestinos,
            comando.modoPadrao(),
            comando.tamanhoLote(),
            cron,
            calculadoraAgendamento.proxima(cron, agora),
            true,
            mapeamentos,
            agora,
            agora);
        validadorEsquema.exigirValido(plano, origem, destinos);
        repositorioPlanos.salvar(plano);
        return plano;
    }

    @Transactional(readOnly = true)
    public ReplicationPlan buscar(UUID id) {
        return repositorioPlanos.buscarPorId(id).orElseThrow(() -> new NotFoundException("Plano de replicacao", id));
    }

    @Transactional(readOnly = true)
    public List<ReplicationPlan> listar() {
        return repositorioPlanos.buscarTodos();
    }

    @Transactional
    public ReplicationPlan atualizar(UUID id, CreateReplicationPlanCommand comando) {
        ReplicationPlan atual = buscar(id);
        exigirSemExecucaoAtiva(id);
        String nome = comando.nome() == null ? "" : comando.nome().trim();
        repositorioPlanos.buscarPorNome(nome)
            .filter(encontrado -> !encontrado.id().equals(id))
            .ifPresent(encontrado -> {
                throw new ConflictException("Ja existe um plano de replicacao chamado '" + nome + "'");
            });

        Connector origem = exigirConector(comando.conectorOrigemId());
        if (origem.papel() != ConnectorRole.ORIGEM) {
            throw new DomainException("A origem do plano deve referenciar um conector ORIGEM");
        }
        List<UUID> idsDestinos = comando.idsConectoresDestino() == null
            ? List.of() : comando.idsConectoresDestino().stream().distinct().toList();
        List<Connector> destinos = idsDestinos.stream().map(this::exigirConector).toList();
        for (Connector destino : destinos) {
            if (destino.papel() != ConnectorRole.DESTINO) {
                throw new DomainException("Os destinos do plano devem referenciar conectores DESTINO: " + destino.id());
            }
        }

        List<CreateTableMappingCommand> comandosMapeamento =
            comando.mapeamentos() == null ? List.of() : comando.mapeamentos();
        List<TableMapping> mapeamentos = IntStream.range(0, comandosMapeamento.size())
            .mapToObj(indice -> paraMapeamento(indice, comandosMapeamento.get(indice)))
            .toList();
        Instant agora = relogio.instant();
        String cron = normalizarCron(comando.expressaoCron());
        ReplicationPlan atualizado = new ReplicationPlan(
            atual.id(), nome, origem.id(), idsDestinos, comando.modoPadrao(), comando.tamanhoLote(),
            cron, calculadoraAgendamento.proxima(cron, agora), atual.ativo(), mapeamentos,
            atual.criadoEm(), agora);
        validadorEsquema.exigirValido(atualizado, origem, destinos);
        repositorioPlanos.atualizar(atualizado);
        return atualizado;
    }

    @Transactional
    public ReplicationPlan alterarAtivacao(UUID id, boolean ativo) {
        ReplicationPlan plano = buscar(id);
        exigirSemExecucaoAtiva(id);
        if (ativo) {
            Connector origem = exigirConector(plano.conectorOrigemId());
            List<Connector> destinos = plano.idsConectoresDestino().stream().map(this::exigirConector).toList();
            validadorEsquema.exigirValido(plano, origem, destinos);
        }
        Instant agora = relogio.instant();
        Instant proximaExecucao = ativo
            ? calculadoraAgendamento.proxima(plano.expressaoCron(), agora)
            : null;
        repositorioPlanos.atualizarAtivacao(id, ativo, proximaExecucao, agora);
        return new ReplicationPlan(
            plano.id(), plano.nome(), plano.conectorOrigemId(), plano.idsConectoresDestino(),
            plano.modoPadrao(), plano.tamanhoLote(), plano.expressaoCron(), proximaExecucao,
            ativo, plano.mapeamentos(), plano.criadoEm(), agora);
    }

    @Transactional(readOnly = true)
    public SchemaValidationResult validarEsquema(UUID id) {
        ReplicationPlan plano = buscar(id);
        Connector origem = exigirConector(plano.conectorOrigemId());
        List<Connector> destinos = plano.idsConectoresDestino().stream().map(this::exigirConector).toList();
        return validadorEsquema.validar(plano, origem, destinos);
    }

    private Connector exigirConector(UUID id) {
        return repositorioConectores.buscarPorId(id).orElseThrow(() -> new NotFoundException("Conector", id));
    }

    private void exigirSemExecucaoAtiva(UUID planoId) {
        if (repositorioExecucoes.existePendenteOuEmExecucao(planoId)) {
            throw new ConflictException("Aguarde a execucao ativa do plano terminar antes de altera-lo");
        }
    }

    private TableMapping paraMapeamento(int ordem, CreateTableMappingCommand comando) {
        return new TableMapping(
            UUID.randomUUID(),
            ordem,
            comando.esquemaOrigem(),
            comando.tabelaOrigem(),
            comando.esquemaDestino(),
            comando.tabelaDestino(),
            comando.colunaChave(),
            vazioParaNulo(comando.colunaIncremental()),
            comando.colunas(),
            true);
    }

    private String normalizarCron(String cron) {
        return vazioParaNulo(cron);
    }

    private String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
