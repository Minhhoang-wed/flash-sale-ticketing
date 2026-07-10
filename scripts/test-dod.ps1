# DoD Ngay 1: dat ve tuan tu 101 lan -> lan 101 phai bi tu choi (409)
$BaseUrl = if ($env:BASE_URL) { $env:BASE_URL } else { "http://localhost:8080" }
$EventId = if ($env:EVENT_ID) { $env:EVENT_ID } else { 1 }

Write-Host "== Event truoc khi test =="
Invoke-RestMethod "$BaseUrl/api/events/$EventId" | ConvertTo-Json

$ok = 0; $rejected = 0
for ($i = 1; $i -le 101; $i++) {
    try {
        Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/events/$EventId/reserve" `
            -Headers @{ "X-User-Id" = "user-$i" } | Out-Null
        $ok++; $code = 201
    } catch {
        $rejected++; $code = $_.Exception.Response.StatusCode.value__
    }
    Write-Host "Request $i -> HTTP $code"
}

Write-Host ""
Write-Host "== Ket qua: $ok thanh cong, $rejected bi tu choi =="
Write-Host "== Event sau khi test =="
Invoke-RestMethod "$BaseUrl/api/events/$EventId" | ConvertTo-Json

if ($ok -eq 100 -and $rejected -eq 1) {
    Write-Host "DoD PASSED: dung 100 ve ban ra, request 101 bi tu choi" -ForegroundColor Green
} else {
    Write-Host "DoD FAILED" -ForegroundColor Red
}
