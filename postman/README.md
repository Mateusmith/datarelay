# Testes locais no Postman

A colecao `DataRelay.postman_collection.json` executa um fluxo real e completo sem depender de ambiente remoto do Postman.

## O que a colecao faz

1. Obtem um JWT no Keycloak usando OAuth2 Client Credentials.
2. Cria um conector PostgreSQL de origem e dois de destino.
3. Abre uma conexao JDBC real com a origem.
4. Cria um plano para replicar `clientes` e depois `pedidos`, respeitando a chave estrangeira.
5. Dispara a carga completa e acompanha o processamento assincrono.
6. Repete a mesma chave e comprova que a API nao cria uma segunda execucao.
7. Consulta o historico persistido do plano.

As variaveis e os identificadores sao preenchidos automaticamente pelos testes de cada requisicao. Cada execucao recebe nomes unicos, portanto a colecao pode ser executada novamente sem conflito.

## Executar

Com os containers do `compose.yml` ativos, importe a colecao e use **Run collection**. Nao e necessario selecionar um environment: os enderecos locais e as demais variaveis pertencem a propria colecao.

O resultado esperado e todas as assercoes verdes, validacao de esquema aprovada, uma execucao com status `CONCLUIDA` e as duas tabelas gravadas nos dois bancos de destino.
