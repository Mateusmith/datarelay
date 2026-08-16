package com.datarelay.plan;

import com.datarelay.plan.domain.ReplicationMode;
import com.datarelay.plan.domain.ReplicationPlan;
import com.datarelay.plan.domain.TableMapping;
import com.datarelay.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplicationPlanTest {

    @Test
    void planoIncrementalExigeColunaDeDataEmCadaMapeamento() {
        UUID origem = UUID.randomUUID();
        UUID destino = UUID.randomUUID();
        TableMapping mapeamento = new TableMapping(
            UUID.randomUUID(), 0, "public", "clientes", "public", "clientes",
            "id", null, List.of("id", "nome"), true);

        assertThatThrownBy(() -> new ReplicationPlan(
            UUID.randomUUID(), "clientes", origem, List.of(destino), ReplicationMode.INCREMENTAL,
            100, null, null, true, List.of(mapeamento), Instant.now(), Instant.now()))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("coluna incremental");
    }

    @Test
    void mapeamentoDeveIncluirColunasChaveEIncremental() {
        assertThatThrownBy(() -> new TableMapping(
            UUID.randomUUID(), 0, "public", "clientes", "public", "clientes",
            "id", "atualizado_em", List.of("nome", "email"), true))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("coluna-chave");
    }
}
