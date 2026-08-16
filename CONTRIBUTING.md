# Como contribuir

## Ambiente

1. Use Java 21 e Docker Desktop.
2. Crie uma branch a partir de `main`.
3. Mantenha nomes de arquivos e classes em ingles; regras, variaveis, metodos, tabelas e colunas permanecem em portugues conforme a convencao do projeto.
4. Nao inclua senhas, tokens ou arquivos `.env` no commit.

## Antes do pull request

```powershell
./mvnw.cmd verify
docker compose config --quiet
docker compose build aplicacao
```

Toda mudanca de comportamento precisa de teste. Alteracoes no banco de controle devem usar uma nova migracao Flyway; nunca edite uma migracao que ja foi publicada.

## Commits

Use mensagens objetivas no imperativo, por exemplo:

```text
feat: adiciona reprocessamento por destino
fix: impede regressao do checkpoint incremental
docs: detalha autenticacao da API
```

O pull request deve explicar o problema, a decisao, como foi testado e qualquer impacto de migracao ou compatibilidade.
