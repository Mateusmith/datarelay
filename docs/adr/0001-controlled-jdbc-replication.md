# ADR 0001: Replicacao JDBC controlada

## Status

Aceita

## Decisao

Usar Spring JDBC para os metadados de controle e JDBC direto para acesso dinamico a origens e destinos. Usar paginacao por cursor, transacoes explicitas no destino, pontos de controle por destino e `UPSERT` do PostgreSQL.

## Consequencias

O projeto demonstra explicitamente o comportamento de transacoes e JDBC, permite retomada depois de trabalho parcial e evita gerar modelos ORM para tabelas desconhecidas. A primeira versao e especifica para PostgreSQL e nao captura exclusoes fisicas.
