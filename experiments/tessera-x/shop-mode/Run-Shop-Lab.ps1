param(
    [Parameter(Mandatory)][string]$TesseraBundler,
    [Parameter(Mandatory)][string]$VanillaBundler,
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$Python,
    [string]$WorkDirectory
)
$ErrorActionPreference = 'Stop'
$taskSourceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
if (-not $WorkDirectory) { $WorkDirectory = Join-Path $taskSourceRoot ('build\shop-lab-' + [Guid]::NewGuid().ToString('N')) }
if (Test-Path -LiteralPath $WorkDirectory) { throw 'Choose a new, empty work directory; existing laboratories are preserved.' }
$taskTessera = (Resolve-Path -LiteralPath $TesseraBundler).Path
$taskVanilla = (Resolve-Path -LiteralPath $VanillaBundler).Path
New-Item -ItemType Directory -Path $WorkDirectory -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'src') -Destination $WorkDirectory -Recurse
foreach ($taskScript in @('prepare.py', 'run.py', 'matrix_final.py')) {
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot $taskScript) -Destination $WorkDirectory
}
$taskPrevious = @{}
foreach ($taskName in @('TESSERA_SHOP_JAVA_HOME', 'TESSERA_SHOP_BUNDLER', 'TESSERA_SHOP_VANILLA')) {
    $taskPrevious[$taskName] = [Environment]::GetEnvironmentVariable($taskName, 'Process')
}
try {
    $env:TESSERA_SHOP_JAVA_HOME = $JavaHome
    $env:TESSERA_SHOP_BUNDLER = $taskTessera
    $env:TESSERA_SHOP_VANILLA = $taskVanilla
    & $Python (Join-Path $WorkDirectory 'prepare.py') --final
    if ($LASTEXITCODE -ne 0) { throw 'Preparing the isolated test artifacts failed.' }
    & $Python (Join-Path $WorkDirectory 'matrix_final.py')
    if ($LASTEXITCODE -ne 0) { throw 'A native shop comparison failed. Preserve the logs and result files.' }
    Write-Host "Native shop comparison completed: $WorkDirectory"
} finally {
    foreach ($taskName in $taskPrevious.Keys) {
        [Environment]::SetEnvironmentVariable($taskName, $taskPrevious[$taskName], 'Process')
    }
}
