package com.datarelay.plan.application;

import com.datarelay.shared.domain.DomainException;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Component
public class ScheduleCalculator {

    public Instant proxima(String expressao, Instant depois) {
        if (expressao == null || expressao.isBlank()) {
            return null;
        }
        try {
            ZonedDateTime proxima = CronExpression.parse(expressao).next(depois.atZone(ZoneOffset.UTC));
            if (proxima == null) {
                throw new DomainException("A expressao cron nao possui uma execucao futura");
            }
            return proxima.toInstant();
        } catch (IllegalArgumentException excecao) {
            throw new DomainException("Expressao cron do Spring invalida: " + expressao);
        }
    }
}
