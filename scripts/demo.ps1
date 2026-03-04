param(
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"
$base = $BaseUrl.TrimEnd("/")

Write-Host "Base URL: $base"

$orderPayload = @{
    symbol = "AAPL"
    side = "BUY"
    quantity = 10
    price = 100.50
    orderType = "LIMIT"
} | ConvertTo-Json -Compress

Write-Host "Create order"
$createResponse = curl.exe -s -X POST "$base/orders" -H "Content-Type: application/json" -d $orderPayload
$created = $createResponse | ConvertFrom-Json
$orderId = $created.id
Write-Host "Created orderId: $orderId"
Write-Output $createResponse

Write-Host "Get order"
curl.exe -s "$base/orders/$orderId" | Write-Output

Write-Host "Cancel order"
curl.exe -s -X POST "$base/orders/$orderId/cancel" | Write-Output

Write-Host "Idempotent create"
$idempotencyKey = "demo-" + [Guid]::NewGuid().ToString("N")
Write-Host "Idempotency-Key: $idempotencyKey"
$idemResponse1 = curl.exe -s -X POST "$base/orders" -H "Content-Type: application/json" -H "Idempotency-Key: $idempotencyKey" -d $orderPayload
$idemResponse2 = curl.exe -s -X POST "$base/orders" -H "Content-Type: application/json" -H "Idempotency-Key: $idempotencyKey" -d $orderPayload
$idem1 = $idemResponse1 | ConvertFrom-Json
$idem2 = $idemResponse2 | ConvertFrom-Json
Write-Host "Idempotent orderIds: $($idem1.id) / $($idem2.id)"
Write-Output $idemResponse1
Write-Output $idemResponse2

Write-Host "Fill scenario (waits for simulator)"
$fillResponse = curl.exe -s -X POST "$base/orders" -H "Content-Type: application/json" -d $orderPayload
$fill = $fillResponse | ConvertFrom-Json
$fillId = $fill.id
Start-Sleep -Seconds 2
curl.exe -s "$base/orders/$fillId" | Write-Output
