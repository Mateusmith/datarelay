package com.datarelay.execution;

import com.datarelay.execution.application.ReplicationExecutionService;
import com.datarelay.execution.application.ReplicationRecoveryScheduler;
import com.datarelay.execution.domain.ExecutionLease;
import com.datarelay.execution.domain.ExecutionLock;
import com.datarelay.execution.domain.ReplicationRun;
import com.datarelay.execution.domain.ReplicationRunRepository;
import com.datarelay.execution.domain.RunStatus;
import com.datarelay.execution.domain.TriggerType;
import com.datarelay.plan.domain.ReplicationMode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class ReplicationRecoverySchedulerTest {

    private static final Instant AGORA = Instant.parse("2026-08-16T12:00:00Z");

    @Test
    void devolveExecucaoInterrompidaParaFilaQuandoNenhumaInstanciaPossuiATrava() {
        ReplicationRunRepository repositorio = mock(ReplicationRunRepository.class);
        ReplicationExecutionService servico = mock(ReplicationExecutionService.class);
        ExecutionLock trava = mock(ExecutionLock.class);
        ExecutionLease lease = mock(ExecutionLease.class);
        ReplicationRun execucao = execucaoEmAndamento();
        when(repositorio.buscarEmExecucao(50)).thenReturn(List.of(execucao));
        when(trava.tentarAdquirir(execucao.planoId())).thenReturn(Optional.of(lease));
        var recuperador = new ReplicationRecoveryScheduler(
            repositorio, servico, trava, Clock.fixed(AGORA, ZoneOffset.UTC));

        recuperador.recuperarAoIniciar();

        verify(repositorio).prepararRetomada(
            execucao.id(), "Execucao retomada automaticamente apos interrupcao", AGORA);
        verify(servico).reenfileirarPendentes(100);
        verify(lease).close();
    }

    @Test
    void preservaExecucaoQueAindaPossuiTravaEmOutraInstancia() {
        ReplicationRunRepository repositorio = mock(ReplicationRunRepository.class);
        ReplicationExecutionService servico = mock(ReplicationExecutionService.class);
        ExecutionLock trava = mock(ExecutionLock.class);
        ReplicationRun execucao = execucaoEmAndamento();
        when(repositorio.buscarEmExecucao(50)).thenReturn(List.of(execucao));
        when(trava.tentarAdquirir(execucao.planoId())).thenReturn(Optional.empty());
        var recuperador = new ReplicationRecoveryScheduler(
            repositorio, servico, trava, Clock.fixed(AGORA, ZoneOffset.UTC));

        recuperador.recuperarPeriodicamente();

        verify(repositorio).buscarEmExecucao(50);
        verify(repositorio, never()).prepararRetomada(any(), anyString(), any());
        verify(servico).reenfileirarPendentes(100);
    }

    private ReplicationRun execucaoEmAndamento() {
        return new ReplicationRun(
            UUID.randomUUID(), UUID.randomUUID(), null, null, "chave", TriggerType.MANUAL,
            ReplicationMode.INCREMENTAL, RunStatus.EM_EXECUCAO, AGORA.minusSeconds(30), null,
            0, 0, null, AGORA.minusSeconds(60), List.of());
    }
}
