$ErrorActionPreference = 'Stop'

$urlApi = 'http://localhost:8080'
$urlKeycloak = 'http://localhost:18081'
$sufixo = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

Write-Host '1/7 Obtendo token OAuth2...'
$prazoAutenticacao = (Get-Date).AddSeconds(90)
$token = $null
do {
    try {
        $token = (Invoke-RestMethod -Method Post `
            -Uri "$urlKeycloak/realms/datarelay/protocol/openid-connect/token" `
            -ContentType 'application/x-www-form-urlencoded' `
            -Body @{
                client_id = 'datarelay-cli'
                client_secret = 'datarelay-cli-secret'
                grant_type = 'client_credentials'
            }).access_token
    } catch {
        Start-Sleep -Seconds 2
    }
} while (-not $token -and (Get-Date) -lt $prazoAutenticacao)
if (-not $token) {
    throw 'O Keycloak nao ficou pronto em 90 segundos. Consulte: docker compose logs keycloak'
}
$cabecalhos = @{ Authorization = "Bearer $token" }

function Criar-Conector($nome, $papel, $urlJdbc, $usuario, $referenciaSegredo) {
    $corpo = @{
        nome = "$nome-$sufixo"
        papel = $papel
        urlJdbc = $urlJdbc
        usuario = $usuario
        referenciaSegredo = $referenciaSegredo
    } | ConvertTo-Json
    Invoke-RestMethod -Method Post -Uri "$urlApi/api/v1/conectores" `
        -Headers $cabecalhos -ContentType 'application/json' -Body $corpo
}

Write-Host '2/7 Criando conectores reais...'
$origem = Criar-Conector 'teste-origem' 'ORIGEM' `
    'jdbc:postgresql://banco-origem:5432/origem' 'origem' 'env:DATARELAY_SENHA_ORIGEM'
$destinoUm = Criar-Conector 'teste-destino-um' 'DESTINO' `
    'jdbc:postgresql://banco-destino-um:5432/destino' 'destino' 'env:DATARELAY_SENHA_DESTINO_UM'
$destinoDois = Criar-Conector 'teste-destino-dois' 'DESTINO' `
    'jdbc:postgresql://banco-destino-dois:5432/destino' 'destino' 'env:DATARELAY_SENHA_DESTINO_DOIS'

Write-Host '3/7 Criando plano clientes -> pedidos...'
$corpoPlano = @{
    nome = "teste-pedidos-$sufixo"
    conectorOrigemId = $origem.id
    idsConectoresDestino = @($destinoUm.id, $destinoDois.id)
    modoPadrao = 'INCREMENTAL'
    tamanhoLote = 2
    expressaoCron = $null
    mapeamentos = @(
        @{
            esquemaOrigem = 'public'; tabelaOrigem = 'clientes'
            esquemaDestino = 'public'; tabelaDestino = 'clientes'
            colunaChave = 'id'; colunaIncremental = 'atualizado_em'
            colunas = @('id', 'nome', 'email', 'atualizado_em')
        },
        @{
            esquemaOrigem = 'public'; tabelaOrigem = 'pedidos'
            esquemaDestino = 'public'; tabelaDestino = 'pedidos'
            colunaChave = 'id'; colunaIncremental = 'atualizado_em'
            colunas = @('id', 'cliente_id', 'total', 'status', 'atualizado_em')
        }
    )
} | ConvertTo-Json -Depth 8
$plano = Invoke-RestMethod -Method Post -Uri "$urlApi/api/v1/planos" `
    -Headers $cabecalhos -ContentType 'application/json' -Body $corpoPlano

Write-Host '4/7 Validando esquema dos tres bancos...'
$validacao = Invoke-RestMethod -Method Post `
    -Uri "$urlApi/api/v1/planos/$($plano.id)/validacao-esquema" -Headers $cabecalhos
if (-not $validacao.valido) {
    throw "Esquema invalido: $($validacao.erros -join ' | ')"
}

Write-Host '5/7 Iniciando carga completa idempotente...'
$chave = "teste-carga-$sufixo"
$cabecalhosExecucao = @{
    Authorization = "Bearer $token"
    'Idempotency-Key' = $chave
}
$execucao = Invoke-RestMethod -Method Post `
    -Uri "$urlApi/api/v1/planos/$($plano.id)/execucoes" `
    -Headers $cabecalhosExecucao -ContentType 'application/json' -Body '{"modo":"COMPLETA"}'

Write-Host '6/7 Aguardando processamento assincrono...'
$prazo = (Get-Date).AddSeconds(60)
do {
    Start-Sleep -Milliseconds 500
    $execucao = Invoke-RestMethod -Method Get `
        -Uri "$urlApi/api/v1/execucoes/$($execucao.id)" -Headers $cabecalhos
} while ($execucao.status -in @('NA_FILA', 'EM_EXECUCAO') -and (Get-Date) -lt $prazo)
if ($execucao.status -ne 'CONCLUIDA') {
    throw "Carga terminou com status $($execucao.status): $($execucao.motivoFalha)"
}

Write-Host '7/7 Conferindo registros nos dois destinos...'
$contagemUm = docker compose exec -T banco-destino-um psql -U destino -d destino -Atc `
    "SELECT (SELECT count(*) FROM clientes) || ':' || (SELECT count(*) FROM pedidos)"
$contagemDois = docker compose exec -T banco-destino-dois psql -U destino -d destino -Atc `
    "SELECT (SELECT count(*) FROM clientes) || ':' || (SELECT count(*) FROM pedidos)"
if ($contagemUm.Trim() -ne '2:2' -or $contagemDois.Trim() -ne '2:2') {
    throw "Contagem inesperada. Destino 1=$contagemUm; Destino 2=$contagemDois"
}

[pscustomobject]@{
    Plano = $plano.id
    Execucao = $execucao.id
    Status = $execucao.status
    LinhasEscritas = $execucao.linhasEscritas
    DestinoUm = $contagemUm.Trim()
    DestinoDois = $contagemDois.Trim()
} | Format-List
