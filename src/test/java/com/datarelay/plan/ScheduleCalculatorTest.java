package com.datarelay.plan;

import com.datarelay.plan.application.ScheduleCalculator;
import com.datarelay.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduleCalculatorTest {

    private final ScheduleCalculator calculadora = new ScheduleCalculator();

    @Test
    void calculaExpressaoCronDeSeisCamposEmUtc() {
        Instant proxima = calculadora.proxima("0 */5 * * * *", Instant.parse("2026-08-15T12:02:30Z"));
        assertThat(proxima).isEqualTo(Instant.parse("2026-08-15T12:05:00Z"));
    }

    @Test
    void rejeitaExpressaoCronInvalida() {
        assertThatThrownBy(() -> calculadora.proxima("a cada cinco minutos", Instant.now()))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("Expressao cron do Spring invalida");
    }
}
