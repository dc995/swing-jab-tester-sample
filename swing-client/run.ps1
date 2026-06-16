#requires -Version 5.1
<#
  Compile and launch the FinanApp Stock Trader (Swing, multi-monitor sample).

  Usage:
    .\run.ps1            # compile + run
    .\run.ps1 -BuildOnly # compile only (no window)

  Requires a JDK 17+ (JDK 21 recommended) on PATH or via JAVA_HOME.
#>
[CmdletBinding()]
param(
    [switch]$BuildOnly,
    [ValidateSet('', 'JSMITH', 'MWILSON')]
    [string]$Trader = ''
)

$ErrorActionPreference = 'Stop'
$dir = $PSScriptRoot
$out = Join-Path $dir 'out'

function Resolve-Tool($name) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    if ($env:JAVA_HOME) {
        $p = Join-Path $env:JAVA_HOME "bin\$name.exe"
        if (Test-Path $p) { return $p }
    }
    throw "$name not found on PATH or under JAVA_HOME. Install a JDK 17+ (JDK 21 recommended)."
}

$javac = Resolve-Tool 'javac'
$java  = Resolve-Tool 'java'

New-Item -ItemType Directory -Force -Path $out | Out-Null

Write-Host 'Compiling SwingTraderApp.java...' -ForegroundColor Cyan
& $javac -encoding UTF-8 -d $out (Join-Path $dir 'SwingTraderApp.java')

if ($BuildOnly) {
    Write-Host 'Build OK (compile only).' -ForegroundColor Green
    return
}

Write-Host 'Launching FinanApp Swing Trader (close the Dashboard window to exit)...' -ForegroundColor Green
$appArgs = @('-cp', $out, 'SwingTraderApp')
if ($Trader) { $appArgs += $Trader }   # e.g. .\run.ps1 -Trader JSMITH opens straight to the desktop
& $java @appArgs
