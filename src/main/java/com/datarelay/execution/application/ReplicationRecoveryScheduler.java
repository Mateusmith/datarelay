package com.datarelay.execution.application;

import com.datarelay.execution.domain.ExecutionLock;
import com.datarelay.execution.domain.ReplicationRun;
import com.datarelay.execution.domain.ReplicationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@ConditionalOnProperty(prefix = "datarelay.recuperacao", name = "ativa", havingValue = "true", matchIfMissing = true)
public class ReplicationRecoveryScheduler {

    private static final Logger REGISTRADOR = LoggerFactory.getLogger(ReplicationRecoveryScheduler.class);

    private final ReplicationRunRepository repositorioExecucoes;
    private final ReplicationExecutionService servicoExecucoes;
    private final ExecutionLock travaExecucao;
    private final Clock relogio;

    public ReplicationRecoveryScheduler(ReplicationRunRepository repositorioExecucoes,
                                        ReplicationExecutionService servicoExecucoes,
                                        ExecutionLock travaExecucao,
                                        Clock relogio) {
        this.repositorioExecucoes = repositorioExecucoes;
        this.servicoExecucoes = servicoExecucoes;
        this.travaExecucao = travaExecucao;
        this.relogio = relogio;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recuperarAoIniciar() {
        recuperarEEnfileirar();
    }

    @Scheduled(fixedDelayString = "${datarelay.recuperacao.intervalo:10s}")
    public void recuperarPeriodicamente() {
        recuperarEEnfileirar();
    }

    private void recuperarEEnfileirar() {
        for (ReplicationRun execucao : repositorioExecucoes.buscarEmExecucao(50)) {
            travaExecucao.tentarAdquirir(execucao.planoId()).ifPresent(lease -> {
                try (lease) {
                    repositorioExecucoes.prepararRetomada(
                        execucao.id(), "Execucao retomada automaticamente apos interrupcao", relogio.instant());
                    REGISTRADOR.warn("Execucao interrompida devolvida para a fila: {}", execucao.id());
                }
            });
        }
        servicoExecucoes.reenfileirarPendentes(100);
    }
}
