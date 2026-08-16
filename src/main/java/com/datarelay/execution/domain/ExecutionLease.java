package com.datarelay.execution.domain;

public interface ExecutionLease extends AutoCloseable {

    @Override
    void close();
}
