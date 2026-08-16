package com.datarelay.execution.application;

import com.datarelay.connector.domain.Connector;
import com.datarelay.connector.domain.ConnectorRepository;
import com.datarelay.connector.domain.ConnectorRole;
import com.datarelay.execution.domain.CreatedRun;
import com.datarelay.execution.domain.ExecutionLease;
import com.datarelay.execution.domain.ExecutionLock;
import com.datarelay.execution.domain.ReplicationRun;
import com.datarelay.execution.domain.ReplicationRunRepository;
import com.datarelay.execution.domain.RunStatus;
import com.datarelay.execution.domain.TargetRun;
import com.datarelay.execution.domain.TargetRunStatus;
import com.datarelay.execution.domain.TriggerType;
import com.datarelay.plan.domain.ReplicationMode;
import com.datarelay.plan.domain.ReplicationPlan;
import com.datarelay.plan.domain.ReplicationPlanRepository;
import com.datarelay.replication.ReplicationEngine;
import com.datarelay.replication.ReplicationResult;
import com.datarelay.shared.domain.DomainException;
import com.datarelay.shared.domain.NotFoundException;
import com.datarelay.shared.domain.ConflictException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReplicationExecutionService {

    private static final Logger REGISTRADOR = LoggerFactory.getLogger(ReplicationExecutionService.class);

    private final ReplicationPlanRepository repositorioPlanos;
    private final ConnectorRepository repositorioConectores;
    private final ReplicationRunRepository repositorioExecucoes;
    private final ReplicationEngine motorReplicacao;
    private final ExecutionLock travaExecucao;
    private final TaskExecutor executorTarefas;
    private final MeterRegistry registroMetricas;
    private final Clock relogio;
    private final Set<UUID> execucoesSubmetidas = ConcurrentHashMap.newKeySet();

    public ReplicationExecutionService(
        ReplicationPlanRepository repositorioPlanos,
        ConnectorRepository repositorioConectores,
        ReplicationRunRepository repositorioExecucoes,
        ReplicationEngine motorReplicacao,
        ExecutionLock travaExecucao,
        @Qualifier("executorTarefasReplicacao") TaskExecutor executorTarefas,
        MeterRegistry registroMetricas,
        Clock relogio) {
        this.repositorioPlanos = repositorioPlanos;
        this.repositorioConectores = repositorioConectores;
        this.repositorioExecucoes = repositorioExecucoes;
        this.motorReplicacao = motorReplicacao;
        this.travaExecucao = travaExecucao;
        this.executorTarefas = executorTarefas;
        this.registroMetricas = registroMetricas;
        this.relogio = relogio;
    }

    public ReplicationRun iniciarManual(UUID planoId, String chaveIdempotencia, ReplicationMode modoSolicitado) {
        return iniciar(planoId, chaveIdempotencia, TriggerType.MANUAL, modoSolicitado, null, null);
    }

    public ReplicationRun iniciarAgendada(ReplicationPlan plano) {
        String chave = "agendada:" + plano.proximaExecucaoEm();
        return iniciar(plano.id(), chave, TriggerType.AGENDADA, plano.modoPadrao(), null, null);
    }

    public ReplicationRun reprocessarDestino(UUID execucaoOrigemId, UUID destinoId,
                                              String chaveIdempotencia, ReplicationMode modoSolicitado) {
        ReplicationRun execucaoOrigem = buscar(execucaoOrigemId);
        TargetRun destinoComFalha = execucaoOrigem.destinos().stream()
            .filter(destino -> destino.conectorDestinoId().equals(destinoId))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Destino da execucao", destinoId));
        if (destinoComFalha.status() != TargetRunStatus.FALHOU) {
            throw new ConflictException("Somente um destino com status FALHOU pode ser reprocessado");
        }
        ReplicationMode modo = modoSolicitado == null ? execucaoOrigem.modo() : modoSolicitado;
        return iniciar(execucaoOrigem.planoId(), chaveIdempotencia, TriggerType.REPROCESSAMENTO,
            modo, execucaoOrigem.id(), destinoId);
    }

    public ReplicationRun buscar(UUID execucaoId) {
        return repositorioExecucoes.buscarPorId(execucaoId)
            .orElseThrow(() -> new NotFoundException("Execucao de replicacao", execucaoId));
    }

    public List<ReplicationRun> listarPorPlano(UUID planoId, int limite) {
        exigirPlano(planoId);
        int limiteSeguro = Math.max(1, Math.min(limite, 100));
        return repositorioExecucoes.buscarPorPlanoId(planoId, limiteSeguro);
    }

    public ReplicationRun cancelar(UUID execucaoId) {
        buscar(execucaoId);
        if (!repositorioExecucoes.cancelarNaFila(execucaoId, relogio.instant())) {
            throw new ConflictException("Somente uma execucao com status NA_FILA pode ser cancelada");
        }
        registroMetricas.counter("datarelay.execucoes", "status", "cancelada").increment();
        return buscar(execucaoId);
    }

    private ReplicationRun iniciar(UUID planoId, String chaveIdempotencia, TriggerType tipoDisparo,
                                   ReplicationMode modoSolicitado, UUID execucaoOrigemId,
                                   UUID conectorDestinoRestritoId) {
        ReplicationPlan plano = exigirPlano(planoId);
        if (!plano.ativo()) {
            throw new DomainException("O plano de replicacao esta desativado");
        }
        String chaveNormalizada = exigirChaveIdempotencia(chaveIdempotencia);
        ReplicationMode modo = modoSolicitado == null ? plano.modoPadrao() : modoSolicitado;
        if (modo == ReplicationMode.INCREMENTAL
            && plano.mapeamentos().stream().anyMatch(mapeamento -> mapeamento.colunaIncremental() == null)) {
            throw new DomainException("Cada mapeamento precisa de uma coluna incremental para a execucao incremental");
        }

        CreatedRun execucaoCriada = repositorioExecucoes.criarSeAusente(
            planoId, chaveNormalizada, tipoDisparo, modo, execucaoOrigemId,
            conectorDestinoRestritoId, relogio.instant());
        if (execucaoCriada.criada()) {
            submeter(execucaoCriada.execucao().id());
        }
        return execucaoCriada.execucao();
    }

    private void submeter(UUID execucaoId) {
        if (!execucoesSubmetidas.add(execucaoId)) {
            return;
        }
        try {
            executorTarefas.execute(() -> {
                try {
                    executar(execucaoId);
                } finally {
                    execucoesSubmetidas.remove(execucaoId);
                }
            });
        } catch (TaskRejectedException excecao) {
            execucoesSubmetidas.remove(execucaoId);
            repositorioExecucoes.concluir(execucaoId, RunStatus.FALHOU, 0, 0,
                "A fila de execucao esta cheia", relogio.instant());
            throw new DomainException("A fila de execucao de replicacao esta cheia");
        }
    }

    public void reenfileirarPendentes(int limite) {
        repositorioExecucoes.buscarIdsNaFila(limite).forEach(this::submeter);
    }

    void executar(UUID execucaoId) {
        ReplicationRun execucaoAguardando = buscar(execucaoId);
        ExecutionLease lease = travaExecucao.tentarAdquirir(execucaoAguardando.planoId()).orElse(null);
        if (lease == null) {
            return;
        }
        try (lease) {
            if (!repositorioExecucoes.marcarEmExecucao(execucaoId, relogio.instant())) {
                return;
            }
            MDC.put("execucaoId", execucaoId.toString());
            long totalLidas = 0;
            long totalEscritas = 0;
            Timer.Sample amostraDuracao = Timer.start(registroMetricas);
            try {
                ReplicationRun execucao = buscar(execucaoId);
                ReplicationPlan plano = exigirPlano(execucao.planoId());
                Connector origem = exigirConector(plano.conectorOrigemId());
                if (origem.papel() != ConnectorRole.ORIGEM) {
                    throw new DomainException("O conector de origem configurado deixou de possuir o papel ORIGEM");
                }

                List<UUID> destinosExecucao = execucao.conectorDestinoRestritoId() == null
                    ? plano.idsConectoresDestino()
                    : List.of(execucao.conectorDestinoRestritoId());
                int destinosConcluidos = 0;
                List<String> falhas = new ArrayList<>();
                for (UUID destinoId : destinosExecucao) {
                    ResultadoDestino resultadoDestino = replicarDestino(execucao, plano, origem, destinoId);
                    totalLidas += resultadoDestino.linhasLidas();
                    totalEscritas += resultadoDestino.linhasEscritas();
                    if (resultadoDestino.concluido()) {
                        destinosConcluidos++;
                    } else {
                        falhas.add(resultadoDestino.motivoFalha());
                    }
                }

                RunStatus status = determinarStatus(destinosConcluidos, destinosExecucao.size());
                String motivoFalha = falhas.isEmpty() ? null : limitar(String.join(" | ", falhas));
                repositorioExecucoes.concluir(
                    execucaoId, status, totalLidas, totalEscritas, motivoFalha, relogio.instant());
                registroMetricas.counter("datarelay.execucoes", "status", status.name().toLowerCase()).increment();
                registroMetricas.counter("datarelay.linhas.escritas").increment(totalEscritas);
                registrarDuracao(amostraDuracao, status);
                REGISTRADOR.info("Execucao de replicacao concluida com status={}, linhasEscritas={}", status, totalEscritas);
            } catch (Exception excecao) {
                String motivo = mensagemSegura(excecao);
                repositorioExecucoes.concluir(
                    execucaoId, RunStatus.FALHOU, totalLidas, totalEscritas, motivo, relogio.instant());
                registroMetricas.counter("datarelay.execucoes", "status", "falhou").increment();
                registrarDuracao(amostraDuracao, RunStatus.FALHOU);
                REGISTRADOR.error("Falha na execucao de replicacao", excecao);
            } finally {
                MDC.remove("execucaoId");
            }
        }
    }

    private ResultadoDestino replicarDestino(ReplicationRun execucao, ReplicationPlan plano,
                                          Connector origem, UUID destinoId) {
        TargetRun execucaoDestino = repositorioExecucoes.iniciarDestino(
            execucao.id(), destinoId, relogio.instant());
        long linhasLidas = 0;
        long linhasEscritas = 0;
        try {
            Connector destino = exigirConector(destinoId);
            if (destino.papel() != ConnectorRole.DESTINO) {
                throw new DomainException(
                    "O conector de destino configurado deixou de possuir o papel DESTINO: " + destinoId);
            }
            for (var mapeamento : plano.mapeamentos()) {
                ReplicationResult resultado = motorReplicacao.replicar(
                    plano, origem, destino, mapeamento, execucao.modo());
                linhasLidas += resultado.linhasLidas();
                linhasEscritas += resultado.linhasEscritas();
            }
            repositorioExecucoes.concluirDestino(execucaoDestino.id(), TargetRunStatus.CONCLUIDA,
                linhasLidas, linhasEscritas, null, relogio.instant());
            return new ResultadoDestino(true, linhasLidas, linhasEscritas, null);
        } catch (Exception excecao) {
            String motivo = "destino=" + destinoId + ": " + mensagemSegura(excecao);
            repositorioExecucoes.concluirDestino(execucaoDestino.id(), TargetRunStatus.FALHOU,
                linhasLidas, linhasEscritas, motivo, relogio.instant());
            REGISTRADOR.error("Falha na replicacao para o destinoId={}", destinoId, excecao);
            return new ResultadoDestino(false, linhasLidas, linhasEscritas, motivo);
        }
    }

    private ReplicationPlan exigirPlano(UUID planoId) {
        return repositorioPlanos.buscarPorId(planoId)
            .orElseThrow(() -> new NotFoundException("Plano de replicacao", planoId));
    }

    private Connector exigirConector(UUID conectorId) {
        return repositorioConectores.buscarPorId(conectorId)
            .orElseThrow(() -> new NotFoundException("Conector", conectorId));
    }

    private String exigirChaveIdempotencia(String valor) {
        if (valor == null || valor.isBlank() || valor.length() > 150) {
            throw new DomainException("O cabecalho Idempotency-Key deve conter entre 1 e 150 caracteres");
        }
        return valor.trim();
    }

    private RunStatus determinarStatus(int sucessos, int quantidadeDestinos) {
        if (sucessos == quantidadeDestinos) {
            return RunStatus.CONCLUIDA;
        }
        return sucessos == 0 ? RunStatus.FALHOU : RunStatus.PARCIALMENTE_CONCLUIDA;
    }

    private String mensagemSegura(Exception excecao) {
        String mensagem = excecao.getMessage();
        return limitar(mensagem == null || mensagem.isBlank() ? excecao.getClass().getSimpleName() : mensagem);
    }

    private String limitar(String valor) {
        return valor.length() <= 2_000 ? valor : valor.substring(0, 2_000);
    }

    private void registrarDuracao(Timer.Sample amostra, RunStatus status) {
        amostra.stop(Timer.builder("datarelay.execucao.duracao")
            .description("Duracao das execucoes de replicacao")
            .tag("status", status.name().toLowerCase())
            .register(registroMetricas));
    }

    private record ResultadoDestino(boolean concluido, long linhasLidas, long linhasEscritas, String motivoFalha) {
    }
}
