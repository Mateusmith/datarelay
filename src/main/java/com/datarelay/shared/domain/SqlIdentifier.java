package com.datarelay.shared.domain;

import java.util.regex.Pattern;

public final class SqlIdentifier {

    private static final Pattern PERMITIDO = Pattern.compile("[a-z_][a-z0-9_]*");

    private SqlIdentifier() {
    }

    public static String exigirValido(String valor, String rotulo) {
        if (valor == null || !PERMITIDO.matcher(valor).matches() || valor.length() > 63) {
            throw new DomainException(rotulo + " deve ser um identificador PostgreSQL em letras minusculas");
        }
        return valor;
    }

    public static String citar(String valor) {
        return "\"" + exigirValido(valor, "Identificador SQL") + "\"";
    }
}
