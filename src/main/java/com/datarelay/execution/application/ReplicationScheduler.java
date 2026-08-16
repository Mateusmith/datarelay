package com.datarelay.execution.application;

import com.datarelay.plan.application.ScheduleCalculator;
import com.datarelay.plan.domain.ReplicationPlan;
import com.datarelay.plan.domain.ReplicationPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@ConditionalOnProperty(prefix = "datarelay.agendador", name = "ativo", havingValue = "true", matchIfMissing = true)
public class ReplicationScheduler {

    private static final Logger REGISTRADOR = LoggerFactory.getLogger(ReplicationScheduler.class);

    private final ReplicationPlanRepository repositorioPlanos;
    private final ReplicationExecutionService servicoExecucoes;
    private final ScheduleCalculator calculadoraAgendamento;
    private final Clock relogio;

    public ReplicationScheduler(ReplicationPlanRepository repositorioPlanos,
                                ReplicationExecutionService servicoExecucoes,
                                ScheduleCalculator calculadoraAgendamento,
                                Clock relogio) {
        this.repositorioPlanos = repositorioPlanos;
        this.servicoExecucoes = servicoExecucoes;
        this.calculadoraAgendamento = calculadoraAgendamento;
        this.relogio = relogio;
    }

    @Scheduled(fixedDelayString = "${datarelay.agendador.intervalo-consulta:30s}")
    public void enfileirarPlanosVencidos() {
        Instant agora = relogio.instant();
        for (ReplicationPlan plano : repositorioPlanos.buscarVencidos(agora, 25)) {
            try {
                servicoExecucoes.iniciarAgendada(plano);
                Instant proxima = calculadoraAgendamento.proxima(
                    plano.expressaoCron(), plano.proximaExecucaoEm());
                repositorioPlanos.atualizarProximaExecucao(plano.id(), proxima, agora);
            } catch (Exception excecao) {
                REGISTRADOR.error("Nao foi possivel enfileirar o plano agendado id={}", plano.id(), excecao);
            }
        }
    }
}
