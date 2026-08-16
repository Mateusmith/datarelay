package com.datarelay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class DataRelayApplication {

    public static void main(String[] argumentos) {
        SpringApplication.run(DataRelayApplication.class, argumentos);
    }
}
