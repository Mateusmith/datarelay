$ErrorActionPreference = 'Stop'

Write-Warning 'Este comando remove os volumes locais do Replicador de Dados e todos os dados de demonstracao.'
docker compose down --volumes --remove-orphans
docker compose up --build --detach
docker compose ps
