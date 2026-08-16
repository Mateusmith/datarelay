package com.datarelay.connector.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConnectorRepository {

    void salvar(Connector conector);

    void atualizar(Connector conector);

    Optional<Connector> buscarPorId(UUID id);

    Optional<Connector> buscarPorNome(String nome);

    List<Connector> buscarTodos();
}
