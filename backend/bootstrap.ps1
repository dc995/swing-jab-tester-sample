#Requires -Version 5.1
<#
.SYNOPSIS
    FinanApp Stock Trader — First-time bootstrap & environment setup.

.DESCRIPTION
    Checks and installs all prerequisites (Java 21, Maven, SQL Server),
    validates the JDBC auth DLL, creates the database schema, loads seed
    data for both trader profiles, and compiles the project so `start.ps1`
    can launch it immediately.

    Safe to re-run — schema and seed steps are skipped when an initialised
    database is already detected.  Use -ResetData to wipe and reload data.

.PARAMETER ResetData
    Drop and recreate the finanapp database tables then re-seed both
    trader profiles.  ⚠  DESTRUCTIVE — all trade history will be lost.

.PARAMETER SkipBuild
    Skip the Maven compile step (useful when re-running after a code change
    has already been compiled separately).

.PARAMETER Force
    Suppress all interactive "are you sure?" prompts.

.EXAMPLE
    .\bootstrap.ps1

.EXAMPLE
    .\bootstrap.ps1 -ResetData -Force
#>
[CmdletBinding()]
param(
    [switch]$ResetData,
    [switch]$SkipBuild,
    [switch]$Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ─── Project root (location of this script) ───────────────────────────────────
$Root = $PSScriptRoot
Push-Location $Root

# ─── Helpers ──────────────────────────────────────────────────────────────────
function Write-Header([string]$msg) {
    Write-Host "`n━━━  $msg  ━━━" -ForegroundColor Cyan
}
function Write-OK([string]$msg)   { Write-Host "  ✔  $msg" -ForegroundColor Green }
function Write-WARN([string]$msg) { Write-Host "  ⚠  $msg" -ForegroundColor Yellow }
function Write-FAIL([string]$msg) { Write-Host "  ✖  $msg" -ForegroundColor Red }
function Write-INFO([string]$msg) { Write-Host "     $msg" -ForegroundColor Gray }

function Confirm-Action([string]$prompt) {
    if ($Force) { return $true }
    $answer = Read-Host "$prompt [y/N]"
    return ($answer -imatch '^y')
}

function Invoke-Sqlcmd-File([string]$server, [string]$file, [string]$database = "") {
    $dbArgs = if ($database) { @("-d", $database) } else { @() }
    $output = & sqlcmd -S $server -E @dbArgs -i $file 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-FAIL "sqlcmd failed (exit $LASTEXITCODE)"
        $output | ForEach-Object { Write-INFO $_ }
        throw "SQL execution failed for: $file"
    }
    return $output
}

function Test-SqlServerReachable([string]$server) {
    $result = & sqlcmd -S $server -E -Q "SELECT 1" -h -1 2>&1
    return ($LASTEXITCODE -eq 0)
}

# ─── Banner ───────────────────────────────────────────────────────────────────
Clear-Host
Write-Host @"
╔══════════════════════════════════════════════════════════════╗
║          FinanApp Stock Trader  —  Bootstrap Setup           ║
╚══════════════════════════════════════════════════════════════╝
  Project : $Root
  Date    : $(Get-Date -Format "yyyy-MM-dd HH:mm")
"@ -ForegroundColor DarkCyan

# ─── 1. Java 21+ ──────────────────────────────────────────────────────────────
Write-Header "Java 21+"

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    Write-FAIL "Java not found on PATH."
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        Write-INFO "Installing Microsoft Build of OpenJDK 21 via winget..."
        winget install --id Microsoft.OpenJDK.21 --exact --accept-package-agreements --accept-source-agreements
        # Refresh PATH
        $env:PATH = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" +
                    [System.Environment]::GetEnvironmentVariable("Path", "User")
        $javaCmd = Get-Command java -ErrorAction SilentlyContinue
        if (-not $javaCmd) {
            Write-FAIL "Java still not on PATH after install. Open a new terminal and re-run."
            exit 1
        }
    } else {
        Write-FAIL "winget not available. Install Java 21 manually:"
        Write-INFO "  https://learn.microsoft.com/java/openjdk/download"
        exit 1
    }
}

$javaVersion = (& java -version 2>&1) -join " "
if ($javaVersion -match 'version "(\d+)') {
    $javaMajor = [int]$Matches[1]
    if ($javaMajor -lt 21) {
        Write-FAIL "Java $javaMajor detected — version 21+ required."
        Write-INFO "  Install via: winget install Microsoft.OpenJDK.21"
        exit 1
    }
    Write-OK "Java $javaMajor  ($javaVersion)"
} else {
    Write-WARN "Could not parse Java version: $javaVersion"
    Write-INFO "Continuing — if the build fails, verify java -version manually."
}

# ─── 2. Maven 3.8+ ────────────────────────────────────────────────────────────
Write-Header "Apache Maven 3.8+"

$mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvnCmd) {
    Write-FAIL "mvn not found on PATH."
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        Write-INFO "Installing Apache Maven via winget..."
        winget install --id Apache.Maven --exact --accept-package-agreements --accept-source-agreements
        $env:PATH = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" +
                    [System.Environment]::GetEnvironmentVariable("Path", "User")
        $mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
        if (-not $mvnCmd) {
            Write-FAIL "mvn still not on PATH after install. Open a new terminal and re-run."
            exit 1
        }
    } else {
        Write-FAIL "winget not available. Install Maven manually:"
        Write-INFO "  https://maven.apache.org/download.cgi"
        exit 1
    }
}

$mvnVersion = (& mvn --version 2>&1 | Select-Object -First 1) -replace "Apache Maven ",""
Write-OK "Maven $mvnVersion"

# ─── 3. sqlcmd ────────────────────────────────────────────────────────────────
Write-Header "sqlcmd (SQL Server command-line tools)"

$sqlcmdCmd = Get-Command sqlcmd -ErrorAction SilentlyContinue
if (-not $sqlcmdCmd) {
    Write-FAIL "sqlcmd not found on PATH."
    Write-INFO "Install SQL Server command-line tools:"
    Write-INFO "  winget install Microsoft.SQLServerCommandLineUtilities"
    Write-INFO "  OR install SQL Server Management Studio (includes sqlcmd)"
    Write-INFO "  https://learn.microsoft.com/sql/tools/sqlcmd/sqlcmd-utility"
    exit 1
}
Write-OK "sqlcmd found at $($sqlcmdCmd.Source)"

# ─── 4. SQL Server service ────────────────────────────────────────────────────
Write-Header "SQL Server service"

$sqlService = $null
# Try default instance first, then any named instance
foreach ($name in @("MSSQLSERVER", "MSSQL`$SQLEXPRESS", "MSSQL`$MSSQLSERVER")) {
    $svc = Get-Service -Name $name -ErrorAction SilentlyContinue
    if ($svc) { $sqlService = $svc; break }
}
if (-not $sqlService) {
    # Fallback: find any SQL Server service
    $sqlService = Get-Service -Name "MSSQL*" -ErrorAction SilentlyContinue |
                  Where-Object { $_.DisplayName -like "SQL Server (*)" } |
                  Select-Object -First 1
}

if (-not $sqlService) {
    Write-FAIL "No SQL Server service found on this machine."
    Write-INFO "Install SQL Server Developer Edition (free):"
    Write-INFO "  https://www.microsoft.com/sql-server/sql-server-downloads"
    Write-INFO "  OR: winget install Microsoft.SQLServer.2022.Developer"
    exit 1
}

if ($sqlService.Status -ne "Running") {
    Write-WARN "SQL Server service '$($sqlService.Name)' is $($sqlService.Status)."
    Write-INFO "Attempting to start service (requires admin privileges)..."
    try {
        Start-Service $sqlService.Name
        Start-Sleep -Seconds 3
        $sqlService.Refresh()
        Write-OK "SQL Server service started."
    } catch {
        Write-FAIL "Could not start SQL Server service: $_"
        Write-INFO "Run as Administrator, or start the service manually via services.msc"
        exit 1
    }
} else {
    Write-OK "SQL Server service '$($sqlService.Name)' is Running"
}

# ─── 5. SQL Server connectivity (TCP/IP on port 1433) ─────────────────────────
Write-Header "SQL Server TCP connectivity (localhost:1433)"

$tcpTest = Test-NetConnection -ComputerName localhost -Port 1433 -WarningAction SilentlyContinue
if (-not $tcpTest.TcpTestSucceeded) {
    Write-WARN "Cannot reach localhost:1433 — TCP/IP may be disabled for SQL Server."
    Write-INFO ""
    Write-INFO "To enable TCP/IP in SQL Server Configuration Manager:"
    Write-INFO "  1. Open 'SQL Server Configuration Manager'"
    Write-INFO "  2. SQL Server Network Configuration → Protocols → TCP/IP → Enable"
    Write-INFO "  3. Double-click TCP/IP → IP Addresses tab → IPAll → TCP Port = 1433"
    Write-INFO "  4. Restart the SQL Server service"
    Write-INFO ""
    Write-INFO "Or run this PowerShell block as Administrator to enable it automatically:"
    Write-INFO '  $k = "HKLM:\SOFTWARE\Microsoft\Microsoft SQL Server"'
    Write-INFO '  $inst = Get-ItemProperty "$k\Instance Names\SQL" | Select -First 1'
    Write-INFO '  # Then set Enabled=1 and TcpPort=1433 under the TCP registry key'
    Write-INFO ""
    if (-not $Force) {
        $continue = Confirm-Action "Continue anyway? (bootstrap will fail at the DB step if unreachable)"
        if (-not $continue) { exit 1 }
    }
} else {
    Write-OK "localhost:1433 is reachable"

    # Quick SQL auth test
    if (Test-SqlServerReachable "localhost") {
        Write-OK "Windows Integrated Security authentication: OK"
    } else {
        Write-WARN "Connected to port 1433 but SQL login failed."
        Write-INFO "Ensure your Windows account has SQL Server access (sysadmin or db_owner)."
    }
}

# ─── 6. JDBC Auth DLL ─────────────────────────────────────────────────────────
Write-Header "JDBC Windows Auth DLL (mssql-jdbc_auth)"

# Parse the configured library path from pom.xml
$pomPath = Join-Path $Root "pom.xml"
$pomXml  = [xml](Get-Content $pomPath -Raw)
$jvmArgs = $pomXml.project.build.plugins.plugin |
           Where-Object { $_.artifactId -eq "spring-boot-maven-plugin" } |
           ForEach-Object { $_.configuration.jvmArguments }

$configuredDllDir = ""
if ($jvmArgs -match 'java\.library\.path=([^"]+)') {
    $configuredDllDir = $Matches[1].TrimEnd("\")
}

$dllFound     = $false
$dllFoundPath = ""

# Check configured path first
if ($configuredDllDir -and (Test-Path $configuredDllDir)) {
    $dll = Get-ChildItem $configuredDllDir -Filter "mssql-jdbc_auth*.x64.dll" -ErrorAction SilentlyContinue |
           Select-Object -First 1
    if ($dll) {
        Write-OK "DLL found at configured path: $($dll.FullName)"
        $dllFound     = $true
        $dllFoundPath = $configuredDllDir
    }
}

# Fall back to local lib/ folder
if (-not $dllFound) {
    $libDir = Join-Path $Root "lib"
    $dll = Get-ChildItem $libDir -Filter "mssql-jdbc_auth*.x64.dll" -ErrorAction SilentlyContinue |
           Sort-Object Name -Descending | Select-Object -First 1
    if ($dll) {
        Write-WARN "DLL not found at configured path: '$configuredDllDir'"
        Write-OK   "Found DLL in project lib\ folder: $($dll.Name)"

        # Update pom.xml to use the local lib/ path
        Write-INFO "Updating pom.xml to use project lib\ folder..."
        $newLibPath = (Join-Path $Root "lib") -replace '\\', '\\'
        # Use regex replace on raw text to avoid XML namespace issues
        $pomRaw = Get-Content $pomPath -Raw
        if ($configuredDllDir) {
            $escapedOld = [regex]::Escape($configuredDllDir)
            $pomRaw = $pomRaw -replace $escapedOld, ($newLibPath -replace '\\\\', '\')
        } else {
            # If jvmArguments doesn't exist yet add it
            $pomRaw = $pomRaw -replace '(<artifactId>spring-boot-maven-plugin</artifactId>\s*)', "`$1<configuration><jvmArguments>`"-Djava.library.path=$($newLibPath -replace '\\\\','\')`"</jvmArguments></configuration>"
        }
        Set-Content $pomPath $pomRaw -Encoding UTF8
        Write-OK "pom.xml updated: java.library.path → $libDir"
        $dllFound     = $true
        $dllFoundPath = $libDir
    }
}

# Check Maven cache as last resort
if (-not $dllFound) {
    $m2Cache = Join-Path $env:USERPROFILE ".m2\repository\com\microsoft\sqlserver\mssql-jdbc"
    $dll = Get-ChildItem $m2Cache -Recurse -Filter "mssql-jdbc_auth*.x64.dll" -ErrorAction SilentlyContinue |
           Select-Object -First 1
    if ($dll) {
        Write-WARN "Found DLL in Maven cache: $($dll.FullName)"
        Write-INFO "Copying to project lib\ folder and updating pom.xml..."
        Copy-Item -Force $dll.FullName (Join-Path $Root "lib" $dll.Name)
        $libDir   = Join-Path $Root "lib"
        $pomRaw   = Get-Content $pomPath -Raw
        if ($configuredDllDir) {
            $escapedOld = [regex]::Escape($configuredDllDir)
            $pomRaw = $pomRaw -replace $escapedOld, $libDir
        }
        Set-Content $pomPath $pomRaw -Encoding UTF8
        Write-OK "pom.xml updated: java.library.path → $libDir"
        $dllFound     = $true
        $dllFoundPath = $libDir
    }
}

if (-not $dllFound) {
    Write-WARN "JDBC auth DLL not found."
    Write-INFO "The app will fail to connect with Windows Integrated Security."
    Write-INFO "Download the Microsoft JDBC Driver 13.x for SQL Server:"
    Write-INFO "  https://learn.microsoft.com/sql/connect/jdbc/download-microsoft-jdbc-driver-for-sql-server"
    Write-INFO "Then copy mssql-jdbc_auth-*.x64.dll to: $Root\lib\"
    Write-INFO ""
    if (-not $Force) {
        $continue = Confirm-Action "Continue anyway?"
        if (-not $continue) { exit 1 }
    }
}

# ─── 7. Database setup ────────────────────────────────────────────────────────
Write-Header "Database setup"

$schemaFile   = Join-Path $Root "src\main\resources\schema.sql"
$seedJsmith   = Join-Path $Root "sql\seed-jsmith.sql"
$seedMwilson  = Join-Path $Root "sql\seed-mwilson.sql"

# Detect whether DB already exists and has tables
$dbCheck = (& sqlcmd -S localhost -E -Q `
    "SELECT COUNT(*) FROM sys.databases WHERE name = 'finanapp'" -h -1 2>&1) -join "`n"
$dbExists = ($LASTEXITCODE -eq 0) -and ($dbCheck -match "(?m)^\s*1\s*$")

$tableCount = 0
if ($dbExists) {
    $tCheck = (& sqlcmd -S localhost -E -d finanapp -Q `
        "SELECT COUNT(*) FROM sys.objects WHERE type = 'U'" -h -1 2>&1) -join "`n"
    if ($LASTEXITCODE -eq 0 -and $tCheck -match "(?m)^\s*(\d+)\s*$") {
        $tableCount = [int]$Matches[1]
    }
}

$runDbSetup = $false
if ($ResetData) {
    if (-not $Force) {
        $ok = Confirm-Action "⚠  -ResetData will DROP and recreate all tables. Proceed?"
        if (-not $ok) {
            Write-INFO "Skipping database reset."
        } else {
            $runDbSetup = $true
        }
    } else {
        $runDbSetup = $true
    }
} elseif (-not $dbExists -or $tableCount -eq 0) {
    Write-INFO "Database not initialised — running first-time setup."
    $runDbSetup = $true
} else {
    Write-OK "Database 'finanapp' exists with $tableCount table(s) — skipping schema/seed."
    Write-INFO "Use -ResetData to wipe and reload all data."
}

if ($runDbSetup) {
    Write-INFO "Creating schema (this will RESET existing tables)..."
    Invoke-Sqlcmd-File "localhost" $schemaFile | Out-Null
    Write-OK "Schema created (orders, holdings, audit_log)"

    Write-INFO "Seeding JSMITH (James Smith — aggressive growth portfolio)..."
    Invoke-Sqlcmd-File "localhost" $seedJsmith "finanapp" | Out-Null
    Write-OK "JSMITH: 6 holdings, 12 orders loaded"

    Write-INFO "Seeding MWILSON (Maria Wilson — conservative value portfolio)..."
    Invoke-Sqlcmd-File "localhost" $seedMwilson "finanapp" | Out-Null
    Write-OK "MWILSON: 8 holdings, 13 orders loaded"

    # Quick row-count verification
    $verify = & sqlcmd -S localhost -E -d finanapp -Q `
        "SELECT 'holdings=' + CAST(COUNT(*) AS VARCHAR) FROM dbo.holdings; SELECT 'orders=' + CAST(COUNT(*) AS VARCHAR) FROM dbo.orders" `
        -h -1 2>&1
    $verify | Where-Object { $_ -match "\w+=\d+" } | ForEach-Object { Write-OK "Verified: $_" }
}

# ─── 8. Maven: download dependencies & compile ────────────────────────────────
if (-not $SkipBuild) {
    Write-Header "Maven: download dependencies & compile"
    Write-INFO "This may take a few minutes on first run while Maven downloads JARs..."
    Write-INFO "(artifacts are cached in $env:USERPROFILE\.m2 for future runs)"
    Write-INFO ""

    Push-Location $Root
    & mvn --batch-mode dependency:resolve compile -q
    if ($LASTEXITCODE -ne 0) {
        Write-FAIL "Maven build failed (exit $LASTEXITCODE). Check output above for errors."
        Pop-Location
        exit 1
    }
    Pop-Location
    Write-OK "Dependencies downloaded and project compiled successfully."
} else {
    Write-WARN "Skipping Maven build (-SkipBuild was specified)."
}

# ─── 9. Playwright browser install (for E2E tests) ────────────────────────────
Write-Header "Playwright browser (E2E tests — optional)"
Write-INFO "Playwright Chromium is required only for E2E tests, not to run the app."
Write-INFO "Install/update browsers with:"
Write-INFO "  mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args=`"install chromium`""
Write-INFO "Skip this if you only plan to run unit tests or use the app directly."

# ─── 10. Summary ──────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║                  Bootstrap Complete!                         ║" -ForegroundColor Green
Write-Host "╚══════════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
Write-Host "  Start the application:" -ForegroundColor White
Write-Host "    .\start.ps1" -ForegroundColor Yellow
Write-Host ""
Write-Host "  Reset data at any time:" -ForegroundColor White
Write-Host "    .\bootstrap.ps1 -ResetData" -ForegroundColor Yellow
Write-Host ""
Write-Host "  Run unit tests:" -ForegroundColor White
Write-Host "    mvn test -Dtest=`"com.finanapp.service.TradingServiceTest`"" -ForegroundColor Yellow
Write-Host ""
Write-Host "  API docs (once app is running):" -ForegroundColor White
Write-Host "    http://localhost:9296/swagger-ui.html" -ForegroundColor Cyan
Write-Host ""

Pop-Location
