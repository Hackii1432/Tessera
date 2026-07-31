param(
    [switch]$FullOnly,
    [switch]$KeepRuns,
    [string[]]$Modes
)

$ErrorActionPreference = "Stop"
$repository = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$runRoot = Join-Path $PSScriptRoot "build\runs"
$resolvedRunRoot = [System.IO.Path]::GetFullPath($runRoot)
$resolvedSmokeRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)

if (-not $resolvedRunRoot.StartsWith($resolvedSmokeRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe smoke run directory: $resolvedRunRoot"
}

if ((Test-Path -LiteralPath $resolvedRunRoot) -and -not $KeepRuns) {
    Remove-Item -LiteralPath $resolvedRunRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $resolvedRunRoot -Force | Out-Null

$selectedModes = if ($Modes -and $Modes.Count -gt 0) {
    $Modes
} elseif ($FullOnly) {
    @("full")
} else {
    @("full", "stop-create", "stop-clone", "stop-unload")
}

foreach ($mode in $selectedModes) {
    $workDir = Join-Path $resolvedRunRoot $mode
    New-Item -ItemType Directory -Path $workDir -Force | Out-Null
    Set-Content -LiteralPath (Join-Path $workDir "eula.txt") -Value "eula=true" -Encoding ASCII
    @(
        "server-port=0"
        "online-mode=false"
        "view-distance=3"
        "simulation-distance=2"
        "spawn-protection=0"
        "sync-chunk-writes=false"
    ) | Set-Content -LiteralPath (Join-Path $workDir "server.properties") -Encoding ASCII

    Write-Host "Running Tessera smoke mode: $mode"
    $env:TESSERA_SMOKE_MODE = $mode
    try {
        & (Join-Path $repository "gradlew.bat") `
            ":folia-server:runBundler" `
            "-Ppaper.runWorkDir=$workDir" `
            "-Ppaper.runMemoryGb=2"
        if ($LASTEXITCODE -ne 0) {
            throw "Server process failed in mode $mode with exit code $LASTEXITCODE"
        }
    } finally {
        Remove-Item Env:TESSERA_SMOKE_MODE -ErrorAction SilentlyContinue
    }

    $resultFile = Join-Path $workDir "tessera-smoke-result.json"
    if (-not (Test-Path -LiteralPath $resultFile)) {
        throw "Mode $mode did not produce tessera-smoke-result.json"
    }
    $result = Get-Content -LiteralPath $resultFile -Raw | ConvertFrom-Json
    $expected = if ($mode.StartsWith("stop-")) { "STOP_REQUESTED" } else { "PASS" }
    if ($result.status -ne $expected) {
        throw "Mode $mode returned $($result.status): $($result.failure)"
    }

    $logFile = Join-Path $workDir "logs\latest.log"
    if (Test-Path -LiteralPath $logFile) {
        $unsafe = Select-String -LiteralPath $logFile -Pattern @(
            "Thread ownership",
            "ConcurrentModificationException",
            "deadlock",
            "Watchdog.*stopping server"
        )
        if ($unsafe) {
            throw "Unsafe lifecycle evidence in ${logFile}: $($unsafe.Line -join '; ')"
        }
    }
}

Write-Host "All Tessera runtime-world smoke modes passed."
