param(
    [Parameter(Mandatory = $true)][string]$Fixture,
    [Parameter(Mandatory = $true)][string]$Jar,
    [Parameter(Mandatory = $true)][int]$ExitCode,
    [switch]$ExpectFailure
)

$ErrorActionPreference = 'Stop'
$fixturePath = (Resolve-Path -LiteralPath $Fixture).Path
if (!(Test-Path -LiteralPath (Join-Path $fixturePath '.tessera-disposable-fixture'))) {
    throw 'Not an isolated Tessera console fixture'
}
$checksPath = Join-Path $fixturePath 'console-shutdown-checks.txt'
$statusPath = Join-Path $fixturePath 'console-shutdown-status.txt'
$latestPath = Join-Path $fixturePath 'logs/latest.log'
$checks = Get-Content -LiteralPath $checksPath -Raw
$status = Get-Content -LiteralPath $statusPath -Raw
$latest = Get-Content -LiteralPath $latestPath -Raw
$lines = [regex]::Matches($latest, 'CONSOLE_SHUTDOWN_BURST (\d+)/512')
$sequence = @($lines | ForEach-Object { [int]$_.Groups[1].Value })
$expected = 1..512
$completeBurst = $sequence.Count -eq 512 -and !(Compare-Object $expected $sequence)
$terminalErrors = [regex]::Matches($status, 'An exception occurred processing Appender TerminalConsole').Count
$knownRace = $terminalErrors -gt 0 -and $status.Contains('Terminal has been closed')
$nativeWindows = $checks.Contains('windows=true') -and $checks.Contains('reader=true')
$normalDisable = $checks.Contains('normal-disable=true')
$saved = ($latest.Contains('Saved all worlds') -and $latest.Contains('Saved all player data') -and
    $latest.Contains('All RegionFile I/O tasks to complete'))
$noNativeFaults = $latest -notmatch 'failed to tick:|Thread failed main thread check|Exception stopping the server|Failed to save|Failed to write'
$goodShutdown = ($terminalErrors -eq 0 -and $status -notmatch '\b(?:ERROR|FATAL)\b' -and
    $latest -notmatch 'Terminal has been closed|Appender TerminalConsole')
$passed = ($ExitCode -eq 0 -and $nativeWindows -and $normalDisable -and $completeBurst -and $saved -and $noNativeFaults -and
    $(if ($ExpectFailure) { $knownRace } else { $goodShutdown }))
$result = [ordered]@{
    passed = $passed
    negativeControl = [bool]$ExpectFailure
    fixture = $fixturePath
    jar = (Resolve-Path -LiteralPath $Jar).Path
    sha256 = (Get-FileHash -LiteralPath $Jar -Algorithm SHA256).Hash.ToLowerInvariant()
    exitCode = $ExitCode
    nativeWindows = $nativeWindows
    normalDisable = $normalDisable
    terminalChecks = $checks.Trim()
    burstCount = $sequence.Count
    completeBurst = $completeBurst
    saved = $saved
    terminalErrors = $terminalErrors
    knownRace = $knownRace
    noNativeFaults = $noNativeFaults
}
$result | ConvertTo-Json -Depth 4
if (!$passed) {
    throw 'Console shutdown acceptance failed'
}
