package com.datarelay.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;

@Configuration
public class ExecutionConfiguration {

    @Bean
    Clock relogioSistema() {
        return Clock.systemUTC();
    }

    @Bean(name = "executorTarefasReplicacao", destroyMethod = "shutdown")
    ThreadPoolTaskExecutor executorTarefasReplicacao(
        @Value("${datarelay.execucao.tamanho-nucleo-pool:2}") int tamanhoNucleoPool,
        @Value("${datarelay.execucao.tamanho-maximo-pool:4}") int tamanhoMaximoPool,
        @Value("${datarelay.execucao.capacidade-fila:50}") int capacidadeFila) {
        ThreadPoolTaskExecutor executorTarefas = new ThreadPoolTaskExecutor();
        executorTarefas.setThreadNamePrefix("replicacao-");
        executorTarefas.setCorePoolSize(tamanhoNucleoPool);
        executorTarefas.setMaxPoolSize(tamanhoMaximoPool);
        executorTarefas.setQueueCapacity(capacidadeFila);
        executorTarefas.setWaitForTasksToCompleteOnShutdown(true);
        executorTarefas.setAwaitTerminationSeconds(30);
        executorTarefas.setStrictEarlyShutdown(true);
        return executorTarefas;
    }
}
