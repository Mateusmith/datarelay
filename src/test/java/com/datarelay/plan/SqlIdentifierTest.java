package com.datarelay.plan;

import com.datarelay.shared.domain.DomainException;
import com.datarelay.shared.domain.SqlIdentifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlIdentifierTest {

    @Test
    void aceitaECitaIdentificadoresPostgresEmMinusculas() {
        assertThat(SqlIdentifier.citar("atualizado_em")).isEqualTo("\"atualizado_em\"");
    }

    @Test
    void rejeitaInjecaoENomesNaoControlados() {
        assertThatThrownBy(() -> SqlIdentifier.exigirValido("clientes; DROP TABLE usuarios", "Tabela"))
            .isInstanceOf(DomainException.class)
            .hasMessageContaining("identificador PostgreSQL em letras minusculas");
        assertThatThrownBy(() -> SqlIdentifier.exigirValido("Cliente", "Tabela"))
            .isInstanceOf(DomainException.class);
    }
}
