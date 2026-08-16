# ADR 0002: Trava distribuida e retomada duravel

## Status

Aceita

## Contexto

Uma fila somente em memoria permite duas execucoes concorrentes do mesmo plano e deixa trabalhos presos quando a JVM reinicia. Um mutex Java nao protege multiplas instancias.

## Decisao

Usar `pg_try_advisory_lock` no PostgreSQL de controle, mantendo a conexao durante a execucao. Persistir a fila e verificar periodicamente trabalhos pendentes ou interrompidos. Uma execucao marcada como ativa so e retomada quando a trava correspondente estiver livre.

## Consequencias

- exclusao mutua funciona entre processos;
- a queda da sessao libera a trava automaticamente;
- o pool de controle reserva uma conexao por plano em execucao;
- o pool precisa ser maior que a concorrencia maxima configurada;
- lotes podem ser repetidos depois de uma queda, portanto o `UPSERT` continua obrigatorio.
