# Changelog

Todas as mudancas relevantes seguem o formato do [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) e o versionamento semantico.

## [1.0.1] - 2026-08-16

### Manutencao

- atualizacoes automatizadas agrupadas por ecossistema;
- atualizacoes principais reservadas para migracoes planejadas;
- imagens de build e runtime mantidas deliberadamente no Java LTS 21.
- GitHub Actions migradas para runtimes compativeis com Node.js 24.
- Maven Wrapper marcado como executavel em ambientes Linux.

## [1.0.0] - 2026-08-16

### Adicionado

- replicacao completa e incremental para multiplos destinos;
- mapeamentos ordenados de `clientes` e `pedidos`;
- validacao de esquema, tipos, chaves e dependencias;
- idempotencia, checkpoints monotonos e isolamento de falhas;
- trava distribuida e retomada automatica apos reinicio;
- reprocessamento exclusivo de destino e cancelamento em fila;
- OAuth2/JWT, Swagger, Postman, Prometheus e Grafana;
- testes unitarios, de seguranca e ponta a ponta com Testcontainers;
- Docker Compose, CI, documentacao e exemplos JSON.
