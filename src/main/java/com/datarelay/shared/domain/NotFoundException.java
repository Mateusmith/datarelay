package com.datarelay.shared.domain;

public class NotFoundException extends DomainException {

    public NotFoundException(String recurso, Object id) {
        super(recurso + " nao encontrado: " + id);
    }
}
