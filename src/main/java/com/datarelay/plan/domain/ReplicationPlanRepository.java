package com.datarelay.plan.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReplicationPlanRepository {

    void salvar(ReplicationPlan plano);

    void atualizar(ReplicationPlan plano);

    Optional<ReplicationPlan> buscarPorId(UUID id);

    Optional<ReplicationPlan> buscarPorNome(String nome);

    List<ReplicationPlan> buscarTodos();

    List<ReplicationPlan> buscarVencidos(Instant agora, int limite);

    void atualizarProximaExecucao(UUID planoId, Instant proximaExecucaoEm, Instant atualizadoEm);

    void atualizarAtivacao(UUID planoId, boolean ativo, Instant proximaExecucaoEm, Instant atualizadoEm);

    boolean existePlanoAtivoComConector(UUID conectorId);
}
