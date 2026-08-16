package com.datarelay.connector.domain;

import com.datarelay.shared.domain.DomainException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record Connector(
    UUID id,
    String nome,
    ConnectorRole papel,
    String urlJdbc,
    String usuario,
    String referenciaSegredo,
    boolean ativo,
    Instant criadoEm,
    Instant atualizadoEm
) {
    private static final Pattern PADRAO_REFERENCIA_SEGREDO = Pattern.compile("env:[A-Z][A-Z0-9_]*");

    public Connector {
        Objects.requireNonNull(id, "O id do conector e obrigatorio");
        Objects.requireNonNull(papel, "O papel do conector e obrigatorio");
        Objects.requireNonNull(criadoEm, "A data de criacao do conector e obrigatoria");
        Objects.requireNonNull(atualizadoEm, "A data de atualizacao do conector e obrigatoria");
        nome = exigirTexto(nome, "Nome do conector");
        urlJdbc = exigirTexto(urlJdbc, "JDBC URL");
        usuario = exigirTexto(usuario, "Usuario do banco de dados");
        referenciaSegredo = exigirTexto(referenciaSegredo, "Referencia do segredo");

        if (!urlJdbc.startsWith("jdbc:postgresql://")) {
            throw new DomainException("O DataRelay v1 aceita apenas URLs JDBC do PostgreSQL");
        }
        if (!PADRAO_REFERENCIA_SEGREDO.matcher(referenciaSegredo).matches()) {
            throw new DomainException("A referencia do segredo deve usar o formato env:NOME_DA_VARIAVEL");
        }
    }

    public static Connector criar(UUID id, String nome, ConnectorRole papel, String urlJdbc,
                                   String usuario, String referenciaSegredo, Instant agora) {
        return new Connector(id, nome, papel, urlJdbc, usuario, referenciaSegredo, true, agora, agora);
    }

    public Connector atualizar(String novoNome, String novaUrlJdbc, String novoUsuario,
                               String novaReferenciaSegredo, Instant agora) {
        return new Connector(id, novoNome, papel, novaUrlJdbc, novoUsuario,
            novaReferenciaSegredo, ativo, criadoEm, agora);
    }

    public Connector alterarAtivacao(boolean novoAtivo, Instant agora) {
        return new Connector(id, nome, papel, urlJdbc, usuario, referenciaSegredo,
            novoAtivo, criadoEm, agora);
    }

    private static String exigirTexto(String valor, String rotulo) {
        if (valor == null || valor.isBlank()) {
            throw new DomainException(rotulo + " e obrigatorio");
        }
        return valor.trim();
    }
}
