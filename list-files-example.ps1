# Exemplo de como listar arquivos do Google File Search
# Execute este script após iniciar a aplicação

# Listar todos os arquivos
Invoke-RestMethod -Uri "http://localhost:8080/api/docs/list" -Method Get | ConvertTo-Json -Depth 5

# Ou apenas ver o total de arquivos
$response = Invoke-RestMethod -Uri "http://localhost:8080/api/docs/list" -Method Get
Write-Host "Total de arquivos: $($response.totalFiles)" -ForegroundColor Green

# Listar nomes dos arquivos
Write-Host "`nArquivos armazenados:" -ForegroundColor Cyan
$response.files | ForEach-Object {
    Write-Host "  - $($_.displayName) ($($_.mimeType)) - $($_.sizeBytes) bytes" -ForegroundColor Yellow
}
