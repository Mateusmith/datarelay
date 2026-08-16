package com.datarelay.shared.domain;

public class ConflictException extends DomainException {

    public ConflictException(String mensagem) {
        super(mensagem);
    }
}
