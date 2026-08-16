package com.datarelay.shared.domain;

public class InfrastructureException extends RuntimeException {

    public InfrastructureException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
