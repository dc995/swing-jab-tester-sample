#Requires -Version 5.1
<#
.SYNOPSIS
    FinanApp Stock Trader — Start the development server.

.DESCRIPTION
    Performs quick pre-flight checks (Java, SQL Server, DB connectivity)
    then launches the Spring Boot application via Maven.  Opens the app
    in your default browser once it is ready.

    Run .\bootstrap.ps1 first if this is a new machine or clean checkout.

.PARAMETER Profile
    Spring profile to activate (default: none / uses application.properties).

.PARAMETER NoBrowser
    Skip auto-opening the browser after the app starts.

.EXAMPLE
    .\start.ps1

.EXAMPLE
    .\start.ps1 -NoBrowser
#>
[CmdletBinding()]
param(
    [string]$Profile  = "",
    [switch]$NoBrowser
)

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
Push-Location $Root

# ─── Helpers ──────────────────────────────────────────────────────────────────
function Write-OK([string]$msg)   { Write-Host "  ✔  $msg" -ForegroundColor Green }
function Write-WARN([string]$msg) { Write-Host "  ⚠  $msg" -ForegroundColor Yellow }
function Write-FAIL([string]$msg) { Write-Host "  ✖  $msg" -ForegroundColor Red }
function Write-INFO([string]$msg) { Write-Host "     $msg" -ForegroundColor Gray }

# ─── Banner ───────────────────────────────────────────────────────────────────
Write-Host @"
╔══════════════════════════════════════════════════════════════╗
║           FinanApp Stock Trader  —  Starting Up              ║
╚══════════════════════════════════════════════════════════════╝
"@ -ForegroundColor DarkCyan

# ─── Pre-flight: Java ─────────────────────────────────────────────────────────
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-FAIL "java not found on PATH.  Run .\bootstrap.ps1 to install prerequisites."
    exit 1
}
Write-OK "Java found"

# ─── Pre-flight: Maven ────────────────────────────────────────────────────────
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-FAIL "mvn not found on PATH.  Run .\bootstrap.ps1 to install prerequisites."
    exit 1
}
Write-OK "Maven found"

# ─── Pre-flight: SQL Server ───────────────────────────────────────────────────
$sqlService = Get-Service -Name "MSSQL*" -ErrorAction SilentlyContinue |
              Where-Object { $_.DisplayName -like "SQL Server (*)" } |
              Select-Object -First 1

if (-not $sqlService) {
    $sqlService = Get-Service -Name "MSSQLSERVER" -ErrorAction SilentlyContinue
}

if (-not $sqlService -or $sqlService.Status -ne "Running") {
    Write-FAIL "SQL Server service is not running."
    Write-INFO "Start it manually or run: Start-Service MSSQLSERVER (as Administrator)"
    Write-INFO "If SQL Server is not installed, run .\bootstrap.ps1 for setup guidance."
    exit 1
}
Write-OK "SQL Server service is Running ($($sqlService.Name))"

# ─── Pre-flight: TCP connectivity ─────────────────────────────────────────────
$tcp = Test-NetConnection -ComputerName localhost -Port 1433 -WarningAction SilentlyContinue
if (-not $tcp.TcpTestSucceeded) {
    Write-WARN "localhost:1433 is not reachable — TCP/IP may be disabled."
    Write-INFO "Enable TCP/IP in SQL Server Configuration Manager or run .\bootstrap.ps1"
} else {
    Write-OK "SQL Server port 1433 reachable"
}

# ─── Pre-flight: finanapp database ────────────────────────────────────────────
$dbCheck = (& sqlcmd -S localhost -E `
    -Q "SELECT COUNT(*) FROM sys.databases WHERE name='finanapp'" -h -1 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0 -or $dbCheck -notmatch "(?m)^\s*1\s*$") {
    Write-FAIL "Database 'finanapp' not found or not accessible."
    Write-INFO "Run .\bootstrap.ps1 to create the schema and seed data."
    exit 1
}
Write-OK "Database 'finanapp' exists"

# ─── Read port from application.properties ────────────────────────────────────
$appProps = Join-Path $Root "src\main\resources\application.properties"
$port = 9296   # default
if (Test-Path $appProps) {
    $portLine = Select-String -Path $appProps -Pattern "^server\.port\s*=" | Select-Object -First 1
    if ($portLine -and $portLine.Line -match "=\s*(\d+)") {
        $port = [int]$Matches[1]
    }
}
$appUrl = "http://localhost:$port"

# ─── Build Maven arguments ────────────────────────────────────────────────────
$mvnArgs = @("spring-boot:run", "--batch-mode")
if ($Profile) {
    $mvnArgs += "-Dspring-boot.run.profiles=$Profile"
    Write-INFO "Spring profile: $Profile"
}

# ─── Launch info ──────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "  Starting FinanApp..." -ForegroundColor White
Write-Host "  App URL   : $appUrl" -ForegroundColor Cyan
Write-Host "  Swagger   : $appUrl/swagger-ui.html" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Trader profiles:" -ForegroundColor White
Write-Host "    JSMITH  — James Smith  (Aggressive Growth)" -ForegroundColor Gray
Write-Host "    MWILSON — Maria Wilson (Conservative Value)" -ForegroundColor Gray
Write-Host ""
Write-Host "  Press Ctrl+C to stop the server." -ForegroundColor DarkGray
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor DarkGray
Write-Host ""

# ─── Open browser after a short delay (background job) ────────────────────────
if (-not $NoBrowser) {
    $browserJob = Start-Job -ScriptBlock {
        param($url)
        Start-Sleep -Seconds 12
        Start-Process $url
    } -ArgumentList $appUrl
}

# ─── Launch Spring Boot (blocking — logs stream to console) ───────────────────
try {
    & mvn @mvnArgs
} finally {
    if (-not $NoBrowser -and $browserJob) {
        Stop-Job $browserJob -ErrorAction SilentlyContinue
        Remove-Job $browserJob -ErrorAction SilentlyContinue
    }
    Pop-Location
}
