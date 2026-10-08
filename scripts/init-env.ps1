# Creates .env with random secrets if it does not exist yet. Usage: .\scripts\init-env.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
if (Test-Path .env) { Write-Host '.env already exists - leaving it unchanged'; return }
function New-Secret {
    $bytes = New-Object byte[] 24
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    ([Convert]::ToBase64String($bytes) -replace '[/+=]', '').Substring(0, 24)
}
@("DB_USERNAME=claims", "DB_PASSWORD=$(New-Secret)", "APP_SECURITY_DEMO_PASSWORD=$(New-Secret)") |
    Set-Content -Path .env -Encoding ascii
Write-Host 'Created .env (git-ignored). Demo-user password: see APP_SECURITY_DEMO_PASSWORD in .env'
