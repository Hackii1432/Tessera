param(
    [Parameter(Mandatory = $true)][string]$Java,
    [Parameter(Mandatory = $true)][string]$Jar
)

$ErrorActionPreference = 'Stop'
$taskRepo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$taskJava = (Resolve-Path -LiteralPath $Java).Path
$taskJar = (Resolve-Path -LiteralPath $Jar).Path
$taskFixture = Join-Path $taskRepo ('build/console-shutdown-' + [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())
if (Test-Path -LiteralPath $taskFixture) { throw 'Refusing to reuse a console fixture' }
New-Item -ItemType Directory -Path $taskFixture | Out-Null
New-Item -ItemType Directory -Path (Join-Path $taskFixture 'plugins'),(Join-Path $taskFixture 'cache'),(Join-Path $taskFixture 'config') | Out-Null
'Isolated console shutdown fixture; no production worlds.' | Set-Content -LiteralPath (Join-Path $taskFixture '.tessera-disposable-fixture')
'eula=true' | Set-Content -LiteralPath (Join-Path $taskFixture 'eula.txt')
$taskListener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
$taskListener.Start()
$taskPort = $taskListener.LocalEndpoint.Port
$taskListener.Stop()
$taskFlat = '{"biome":"minecraft:plains","lakes":false,"features":false,"structure_overrides":[],"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}'
@("server-ip=127.0.0.1", "server-port=$taskPort", 'online-mode=false', 'enforce-secure-profile=false',
    'view-distance=2', 'simulation-distance=2', 'level-type=minecraft:flat', "generator-settings=$taskFlat") |
    Set-Content -LiteralPath (Join-Path $taskFixture 'server.properties')
@('_version: 31','threaded-regions:','  threads: 4','spark:','  enabled: false') |
    Set-Content -LiteralPath (Join-Path $taskFixture 'config/paper-global.yml')
Copy-Item -LiteralPath (Join-Path $taskRepo 'test-plugin/build/libs/tessera-runtime-world-smoke.jar') -Destination (Join-Path $taskFixture 'plugins/smoke.jar')
Copy-Item -LiteralPath $taskJar -Destination (Join-Path $taskFixture 'server.jar')
if ((Get-FileHash -LiteralPath $taskJar).Hash -ne (Get-FileHash -LiteralPath (Join-Path $taskFixture 'server.jar')).Hash) { throw 'JAR hash mismatch' }
$taskVanilla = Join-Path $taskRepo 'folia-server/.gradle/caches/paperweight/taskCache/downloadServerJar.jar'
if ((Get-FileHash -LiteralPath $taskVanilla).Hash.ToLowerInvariant() -ne 'd052f14d7a173734fba553711e5b570162e2f2a313267ee31a21b975a679be64') { throw 'Unexpected Mojang bundler' }
Copy-Item -LiteralPath $taskVanilla -Destination (Join-Path $taskFixture 'cache/mojang_26.3.jar')
Write-Output "CONSOLE_FIXTURE $taskFixture PORT $taskPort"
$taskPreviousMode = $env:TESSERA_SMOKE_MODE
try {
    $env:TESSERA_SMOKE_MODE = 'console-shutdown'
    Push-Location -LiteralPath $taskFixture
    try {
        # Run this script in ConPTY/a Windows console, not with Java stdout piped.
        & $taskJava '--enable-native-access=ALL-UNNAMED' '-Dterminal.jline=true' '-Dorg.jline.terminal.type=windows' '-Dorg.jline.terminal.provider=ffm' '-XX:ActiveProcessorCount=4' '-Xms512M' '-Xmx2G' '-jar' 'server.jar' '--nogui'
        $taskExit = $LASTEXITCODE
    } finally { Pop-Location }
    & (Join-Path $PSScriptRoot 'verify.ps1') -Fixture $taskFixture -Jar $taskJar -ExitCode $taskExit |
        Tee-Object -FilePath (Join-Path $taskFixture 'result.json')
    Write-Output 'CONSOLE_RESULT_PASSED'
} finally { $env:TESSERA_SMOKE_MODE = $taskPreviousMode }
