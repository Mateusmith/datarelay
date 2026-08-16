package com.datarelay.execution.domain;

import java.util.Optional;
import java.util.UUID;

public interface ExecutionLock {

    Optional<ExecutionLease> tentarAdquirir(UUID planoId);
}
