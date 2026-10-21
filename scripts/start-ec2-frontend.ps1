param(
    [string]$ApiBaseUrl = 'http://localhost:18080',
    [switch]$Install
)
$ErrorActionPreference = 'Stop'
$api = [Uri]$ApiBaseUrl
if (-not $api.IsAbsoluteUri -or $api.Query -or $api.Fragment -or $api.UserInfo -or $api.AbsolutePath -ne '/') {
    throw 'ApiBaseUrl must be an origin, such as https://email-api.texto-qa.in or http://localhost:18080.'
}
if ($api.Scheme -ne 'https' -and -not ($api.Scheme -eq 'http' -and $api.IsLoopback)) {
    throw 'Use HTTPS for a remote API, or HTTP through a loopback SSH tunnel.'
}
$env:NEXT_PUBLIC_API_BASE_URL = $ApiBaseUrl.TrimEnd('/')
$env:API_BASE_URL = $env:NEXT_PUBLIC_API_BASE_URL
$env:NEXT_PUBLIC_WHATSAPP_BASE_URL = 'https://texto.in'
Set-Location -LiteralPath (Join-Path (Split-Path -Parent $PSScriptRoot) 'frontend')
try {
    $health = Invoke-RestMethod "$env:API_BASE_URL/actuator/health" -TimeoutSec 15
    if ($health.status -ne 'UP') { throw 'Backend health is not UP.' }
} catch {
    throw "Cannot reach a healthy backend at $env:API_BASE_URL. Start the EC2 backend and keep the SSH tunnel open if using localhost. $($_.Exception.Message)"
}
if ($Install) {
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
Write-Host "Frontend uses EC2 API through $env:API_BASE_URL. Open http://localhost:3000."
& npm.cmd run dev -- --hostname localhost --port 3000
exit $LASTEXITCODE
