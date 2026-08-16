# Politica de seguranca

## Versoes suportadas

Enquanto o projeto estiver na serie `1.x`, a versao mais recente recebe correcoes de seguranca.

## Relatar vulnerabilidade

Nao abra uma issue publica com tokens, credenciais, dados pessoais ou detalhes exploraveis. Use **Security > Report a vulnerability** no repositorio GitHub para iniciar uma discussao privada.

Inclua o componente afetado, passos minimos de reproducao, impacto percebido e uma sugestao de correcao quando houver.

## Ambiente local

As credenciais de `compose.yml`, do realm Keycloak e da documentacao sao deliberadamente publicas e servem apenas para demonstracao local. Uma implantacao real deve usar TLS, um gerenciador de segredos, rotacao de credenciais e clientes OAuth2 separados por ambiente.
