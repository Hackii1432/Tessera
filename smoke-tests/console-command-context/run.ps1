param(
    [string]$JarPath,
    [ValidateRange(30, 300)]
    [int]$StartupTimeoutSeconds = 120,
    [ValidateRange(5, 60)]
    [int]$CommandTimeoutSeconds = 10,
    [ValidateRange(5, 120)]
    [int]$ShutdownTimeoutSeconds = 30,
    [switch]$SkipBuild,
    [switch]$KeepRuns
)

$ErrorActionPreference = "Stop"

$smokeRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$repository = [System.IO.Path]::GetFullPath((Join-Path $smokeRoot "..\.."))
$runRoot = [System.IO.Path]::GetFullPath((Join-Path $smokeRoot "build\runs"))
if (-not $runRoot.StartsWith($smokeRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe smoke run directory: $runRoot"
}
if ((Test-Path -LiteralPath $runRoot) -and -not $KeepRuns) {
    Remove-Item -LiteralPath $runRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $runRoot -Force | Out-Null

if (-not $SkipBuild) {
    & (Join-Path $repository "gradlew.bat") `
        ":test-plugin:jar" `
        ":folia-server:createPaperclipJar"
    if ($LASTEXITCODE -ne 0) {
        throw "Building the Paperclip JAR and console smoke plugin failed with exit code $LASTEXITCODE."
    }
}

if ([string]::IsNullOrWhiteSpace($JarPath)) {
    $JarPath = Get-ChildItem -LiteralPath (Join-Path $repository "folia-server\build\libs") `
        -Filter "tessera-server-*.jar" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
$pluginJar = Join-Path $repository "test-plugin\build\libs\tessera-runtime-world-smoke.jar"
if ([string]::IsNullOrWhiteSpace($JarPath) -or -not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
    throw "Paperclip JAR not found. Build it first or pass -JarPath."
}
if (-not (Test-Path -LiteralPath $pluginJar -PathType Leaf)) {
    throw "Console smoke plugin not found: $pluginJar"
}
$JarPath = [System.IO.Path]::GetFullPath($JarPath)
$bundlerRepository = Join-Path $runRoot "paperclip-repository"
New-Item -ItemType Directory -Path $bundlerRepository -Force | Out-Null

function Format-Argument {
    param([Parameter(Mandatory = $true)][string]$Value)
    return '"' + $Value.Replace('"', '\"') + '"'
}

function New-SmokeServer {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [int]$RconPort = 0
    )

    $working = Join-Path $runRoot $Name
    New-Item -ItemType Directory -Path (Join-Path $working "plugins") -Force | Out-Null
    Copy-Item -LiteralPath $pluginJar -Destination (Join-Path $working "plugins") -Force
    Set-Content -LiteralPath (Join-Path $working "eula.txt") -Value "eula=true" -Encoding ASCII
    $properties = @(
        "server-port=0"
        "online-mode=false"
        "view-distance=2"
        "simulation-distance=2"
        "spawn-protection=0"
        "sync-chunk-writes=false"
        "enable-rcon=$($RconPort -gt 0)"
    )
    if ($RconPort -gt 0) {
        $properties += "rcon.port=$RconPort"
        $properties += "rcon.password=tessera-console-smoke"
    }
    $properties | Set-Content -LiteralPath (Join-Path $working "server.properties") -Encoding ASCII

    $arguments = @(
        "-DbundlerRepoDir=$bundlerRepository"
        "-jar"
        $JarPath
        "nogui"
    )
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = (Get-Command java -ErrorAction Stop).Source
    $startInfo.WorkingDirectory = $working
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardInput = $true
    $startInfo.EnvironmentVariables["TESSERA_SMOKE_MODE"] = "console-context"
    if ($startInfo.PSObject.Properties.Name -contains "ArgumentList") {
        foreach ($argument in $arguments) {
            $startInfo.ArgumentList.Add($argument)
        }
    } else {
        $startInfo.Arguments = ($arguments | ForEach-Object { Format-Argument $_ }) -join " "
    }

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw "Server process $Name did not start."
    }
    return [pscustomobject]@{
        Name = $Name
        WorkingDirectory = $working
        Log = Join-Path $working "logs\latest.log"
        Process = $process
    }
}

function Stop-SmokeProcess {
    param([Parameter(Mandatory = $true)]$Server)
    if (-not $Server.Process.HasExited) {
        try {
            $Server.Process.StandardInput.WriteLine("stop")
            $Server.Process.StandardInput.Flush()
        } catch {
            Write-Warning "[$($Server.Name)] Could not send stop: $_"
        }
        if (-not $Server.Process.WaitForExit($ShutdownTimeoutSeconds * 1000)) {
            try {
                $Server.Process.Kill()
            } catch {
                Write-Warning "[$($Server.Name)] Could not terminate process: $_"
            }
            $Server.Process.WaitForExit()
            throw "[$($Server.Name)] Server did not stop within $ShutdownTimeoutSeconds seconds."
        }
    }
}

function Wait-LogPattern {
    param(
        [Parameter(Mandatory = $true)]$Server,
        [Parameter(Mandatory = $true)][string]$Pattern,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $nextProgress = [DateTime]::UtcNow.AddSeconds(5)
    do {
        if (Test-Path -LiteralPath $Server.Log) {
            $content = Get-Content -LiteralPath $Server.Log -Raw
            if ($content -match $Pattern) {
                return $content
            }
        }
        if ($Server.Process.HasExited) {
            $tail = if (Test-Path -LiteralPath $Server.Log) {
                (Get-Content -LiteralPath $Server.Log -Tail 120) -join [Environment]::NewLine
            } else {
                "<log not created>"
            }
            throw "[$($Server.Name)] Server exited before pattern '$Pattern'.`n$tail"
        }
        if ([DateTime]::UtcNow -ge $nextProgress) {
            $remaining = [Math]::Max(0, [int]($deadline - [DateTime]::UtcNow).TotalSeconds)
            Write-Host "[$($Server.Name)] waiting for '$Pattern'; ${remaining}s remain."
            $nextProgress = [DateTime]::UtcNow.AddSeconds(5)
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)

    $tail = if (Test-Path -LiteralPath $Server.Log) {
        (Get-Content -LiteralPath $Server.Log -Tail 120) -join [Environment]::NewLine
    } else {
        "<log not created>"
    }
    throw "[$($Server.Name)] Timed out waiting for '$Pattern' after $TimeoutSeconds seconds.`n$tail"
}

function Assert-NoCommandContextFailure {
    param([Parameter(Mandatory = $true)]$Server)
    $content = Get-Content -LiteralPath $Server.Log -Raw
    $unsafe = @(
        "CommandSourceStack\.getLevel\(\).*null"
        "executeCommandInContext.*NullPointerException"
        "Cannot invoke .*ServerLevel\.getGameRules"
        "Command source .* has no world context"
        "Thread failed main thread check"
        "Cannot getEntities asynchronously"
        "captureTreeGeneration"
        "getCurrentWorldData\(\).*null"
    )
    foreach ($pattern in $unsafe) {
        if ($content -match $pattern) {
            throw "[$($Server.Name)] Found command-context failure '$pattern' in $($Server.Log)."
        }
    }
}

function Get-FreeTcpPort {
    $listener = [System.Net.Sockets.TcpListener]::new(
        [System.Net.IPAddress]::Loopback,
        0
    )
    $listener.Start()
    try {
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    } finally {
        $listener.Stop()
    }
}

function Write-RconPacket {
    param(
        [Parameter(Mandatory = $true)][System.IO.Stream]$Stream,
        [Parameter(Mandatory = $true)][int]$RequestId,
        [Parameter(Mandatory = $true)][int]$Type,
        [Parameter(Mandatory = $true)][string]$Body
    )
    $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
    $buffer = [System.IO.MemoryStream]::new()
    try {
        $writer = [System.IO.BinaryWriter]::new($buffer, [System.Text.Encoding]::UTF8, $true)
        $writer.Write([int](10 + $bodyBytes.Length))
        $writer.Write([int]$RequestId)
        $writer.Write([int]$Type)
        $writer.Write($bodyBytes)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Flush()
        $packet = $buffer.ToArray()

        # Vanilla's RCON reader expects one socket read to contain the complete
        # request, so write the assembled packet in one network-stream call.
        $Stream.Write($packet, 0, $packet.Length)
        $Stream.Flush()
    } finally {
        $buffer.Dispose()
    }
}

function Read-RconPacket {
    param([Parameter(Mandatory = $true)][System.IO.Stream]$Stream)
    $reader = [System.IO.BinaryReader]::new($Stream, [System.Text.Encoding]::UTF8, $true)
    $length = $reader.ReadInt32()
    if ($length -lt 10 -or $length -gt 1MB) {
        throw "Invalid RCON packet length: $length"
    }
    $requestId = $reader.ReadInt32()
    $type = $reader.ReadInt32()
    $body = [System.Text.Encoding]::UTF8.GetString($reader.ReadBytes($length - 10))
    $nullOne = $reader.ReadByte()
    $nullTwo = $reader.ReadByte()
    return [pscustomobject]@{
        RequestId = $requestId
        Type = $type
        Body = $body
        Terminators = @($nullOne, $nullTwo)
    }
}

function Invoke-RconCommand {
    param(
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Command
    )
    $client = [System.Net.Sockets.TcpClient]::new()
    $client.ReceiveTimeout = $CommandTimeoutSeconds * 1000
    $client.SendTimeout = $CommandTimeoutSeconds * 1000
    $client.Connect([System.Net.IPAddress]::Loopback, $Port)
    try {
        $stream = $client.GetStream()
        Write-RconPacket -Stream $stream -RequestId 1 -Type 3 -Body "tessera-console-smoke"
        $auth = Read-RconPacket -Stream $stream
        if ($auth.RequestId -ne 1) {
            throw "RCON authentication failed with request id $($auth.RequestId)."
        }
        Write-RconPacket -Stream $stream -RequestId 2 -Type 2 -Body $Command
        return (Read-RconPacket -Stream $stream).Body
    } finally {
        $client.Dispose()
    }
}

Write-Host "Running startup-buffered console command test."
$buffered = New-SmokeServer -Name "startup-buffered"
try {
    $buffered.Process.StandardInput.WriteLine("tessera-console-context queued-first")
    $buffered.Process.StandardInput.WriteLine(
        "execute in minecraft:overworld run tessera-console-context queued-dimension"
    )
    $buffered.Process.StandardInput.WriteLine("stop")
    $buffered.Process.StandardInput.Flush()

    Wait-LogPattern `
        -Server $buffered `
        -Pattern "TESSERA_CONSOLE_CONTEXT_OK.*queued-dimension" `
        -TimeoutSeconds $StartupTimeoutSeconds | Out-Null
    if (-not $buffered.Process.WaitForExit($ShutdownTimeoutSeconds * 1000)) {
        throw "[startup-buffered] Queued stop command did not stop the server."
    }
    $lines = Get-Content -LiteralPath $buffered.Log
    $doneIndex = [Array]::FindIndex(
        [string[]]$lines,
        [Predicate[string]]{ param($line) $line -match "Done \(" }
    )
    $firstIndex = [Array]::FindIndex(
        [string[]]$lines,
        [Predicate[string]]{ param($line) $line -match "TESSERA_CONSOLE_CONTEXT_OK.*queued-first" }
    )
    $dimensionIndex = [Array]::FindIndex(
        [string[]]$lines,
        [Predicate[string]]{ param($line) $line -match "TESSERA_CONSOLE_CONTEXT_OK.*queued-dimension" }
    )
    if ($doneIndex -lt 0 -or $firstIndex -le $doneIndex -or $dimensionIndex -le $firstIndex) {
        throw "[startup-buffered] Commands were not executed after Done in FIFO order."
    }
    Assert-NoCommandContextFailure -Server $buffered
} finally {
    Stop-SmokeProcess -Server $buffered
}

Write-Host "Running post-start console and RCON command test."
$rconPort = Get-FreeTcpPort
$interactive = New-SmokeServer -Name "interactive-rcon" -RconPort $rconPort
try {
    Wait-LogPattern `
        -Server $interactive `
        -Pattern "TESSERA_CONSOLE_CONTEXT_PLUGIN_READY" `
        -TimeoutSeconds $StartupTimeoutSeconds | Out-Null
    Wait-LogPattern `
        -Server $interactive `
        -Pattern "Done \(" `
        -TimeoutSeconds $StartupTimeoutSeconds | Out-Null

    $fixtures = @(
        "tessera-console-context fixture minecraft:overworld 736 80 736 2"
        "tessera-console-context fixture minecraft:overworld 1760 80 736 1"
        "tessera-console-context fixture minecraft:the_nether 736 80 736 1"
        "tessera-console-context fixture minecraft:the_end 736 80 736 0"
    )
    foreach ($fixture in $fixtures) {
        $interactive.Process.StandardInput.WriteLine($fixture)
    }
    $interactive.Process.StandardInput.Flush()
    foreach ($fixtureReady in @(
        "world=minecraft:overworld chunk=46,46 count=2",
        "world=minecraft:overworld chunk=110,46 count=1",
        "world=minecraft:the_nether chunk=46,46 count=1",
        "world=minecraft:the_end chunk=46,46 count=0"
    )) {
        Wait-LogPattern `
            -Server $interactive `
            -Pattern ("TESSERA_SELECTOR_FIXTURE_READY " + [regex]::Escape($fixtureReady)) `
            -TimeoutSeconds $CommandTimeoutSeconds | Out-Null
    }

    $blockFixtures = @(
        "tessera-console-context block-fixture minecraft:overworld 15 30 15"
        "tessera-console-context block-fixture minecraft:overworld 736 80 736"
        "tessera-console-context block-fixture minecraft:the_nether 736 80 736"
        "tessera-console-context block-fixture minecraft:the_end 736 80 736"
    )
    foreach ($blockFixture in $blockFixtures) {
        $interactive.Process.StandardInput.WriteLine($blockFixture)
    }
    $interactive.Process.StandardInput.Flush()
    foreach ($blockFixtureReady in @(
        "world=minecraft:overworld block=15,30,15",
        "world=minecraft:overworld block=736,80,736",
        "world=minecraft:the_nether block=736,80,736",
        "world=minecraft:the_end block=736,80,736"
    )) {
        Wait-LogPattern `
            -Server $interactive `
            -Pattern ("TESSERA_BLOCK_FIXTURE_READY " + [regex]::Escape($blockFixtureReady)) `
            -TimeoutSeconds $CommandTimeoutSeconds | Out-Null
    }

    $blockCommands = @(
        "execute in minecraft:overworld run forceload add 0 0"
        "execute in minecraft:overworld if block 15 30 15 minecraft:stone run tessera-console-context block-if-overworld"
        "execute in minecraft:overworld unless block 15 30 15 minecraft:barrier run tessera-console-context block-unless-overworld"
        "execute in minecraft:overworld if block 15 30 15 #minecraft:mineable/pickaxe run tessera-console-context block-tag-overworld"
        "execute in minecraft:the_nether if block 736 80 736 minecraft:stone run tessera-console-context block-if-nether"
        "execute in minecraft:the_end if block 736 80 736 minecraft:stone run tessera-console-context block-if-end"
        "execute in minecraft:overworld run data get block 16 30 15 id"
        "execute in minecraft:the_nether run data get block 737 80 736 id"
        "execute in minecraft:the_end run data get block 737 80 736 id"
    )
    foreach ($blockCommand in $blockCommands) {
        $interactive.Process.StandardInput.WriteLine($blockCommand)
    }
    $interactive.Process.StandardInput.Flush()
    foreach ($marker in @(
        "block-if-overworld",
        "block-unless-overworld",
        "block-tag-overworld",
        "block-if-nether",
        "block-if-end"
    )) {
        Wait-LogPattern `
            -Server $interactive `
            -Pattern ("TESSERA_CONSOLE_CONTEXT_OK.*" + $marker) `
            -TimeoutSeconds $CommandTimeoutSeconds | Out-Null
    }
    Wait-LogPattern `
        -Server $interactive `
        -Pattern 'minecraft:chest' `
        -TimeoutSeconds $CommandTimeoutSeconds | Out-Null
    $blockDataLog = Get-Content -LiteralPath $interactive.Log -Raw
    $blockDataResponses = [regex]::Matches($blockDataLog, "minecraft:chest").Count
    if ($blockDataResponses -lt 3) {
        throw "Expected block data responses from all three dimensions, got $blockDataResponses."
    }
    $interactive.Process.StandardInput.WriteLine(
        "execute in minecraft:the_end if block 50000 80 50000 minecraft:stone run say unloaded-console-block"
    )
    $interactive.Process.StandardInput.Flush()
    Wait-LogPattern `
        -Server $interactive `
        -Pattern 'Cannot run a region-bound command: target region minecraft:the_end.*is not loaded' `
        -TimeoutSeconds $CommandTimeoutSeconds | Out-Null

    $selectorCommands = @(
        "execute in minecraft:overworld positioned 736 80 736 if entity @e[type=minecraft:armor_stand,distance=..8] run tessera-console-context selector-overworld-local"
        "execute in minecraft:the_nether positioned 736 80 736 if entity @e[type=minecraft:armor_stand,distance=..8] run tessera-console-context selector-nether-local"
        "execute in minecraft:the_end positioned 736 80 736 unless entity @e[type=minecraft:armor_stand,distance=..8] run tessera-console-context selector-end-empty"
        "execute in minecraft:overworld positioned 736 80 736 if entity @e[type=minecraft:armor_stand,distance=..128] run tessera-console-context selector-overworld-128"
        "execute in minecraft:overworld positioned 736 80 736 as @e[type=minecraft:armor_stand,distance=..8] run tessera-console-context selector-as"
        "execute in minecraft:overworld positioned 736 80 736 at @e[type=minecraft:armor_stand,distance=..8] run tessera-console-context selector-at"
    )
    foreach ($selectorCommand in $selectorCommands) {
        $interactive.Process.StandardInput.WriteLine($selectorCommand)
    }
    $interactive.Process.StandardInput.Flush()
    foreach ($marker in @(
        "selector-overworld-local",
        "selector-nether-local",
        "selector-end-empty",
        "selector-overworld-128",
        "selector-as",
        "selector-at"
    )) {
        Wait-LogPattern `
            -Server $interactive `
            -Pattern ("TESSERA_CONSOLE_CONTEXT_OK.*" + $marker) `
            -TimeoutSeconds $CommandTimeoutSeconds | Out-Null
    }
    $selectorLog = Get-Content -LiteralPath $interactive.Log -Raw
    foreach ($marker in @("selector-as", "selector-at")) {
        $invocations = [regex]::Matches(
            $selectorLog,
            "\[TesseraRuntimeWorldSmoke\].*TESSERA_CONSOLE_CONTEXT_OK.*" + $marker
        ).Count
        if ($invocations -ne 2) {
            throw "Expected two '$marker' invocations for two selector results, got $invocations."
        }
    }

    $interactive.Process.StandardInput.WriteLine("tessera-console-context interactive")
    $interactive.Process.StandardInput.WriteLine(
        "execute in minecraft:overworld run tessera-console-context interactive-dimension"
    )
    $interactive.Process.StandardInput.Flush()
    Wait-LogPattern `
        -Server $interactive `
        -Pattern "TESSERA_CONSOLE_CONTEXT_OK.*interactive-dimension" `
        -TimeoutSeconds $CommandTimeoutSeconds | Out-Null

    $rconResponse = Invoke-RconCommand `
        -Port $rconPort `
        -Command "tessera-console-context rcon"
    if ($rconResponse -notmatch "TESSERA_CONSOLE_CONTEXT_OK") {
        throw "RCON plugin command returned an unexpected response: $rconResponse"
    }
    $rconDimensionResponse = Invoke-RconCommand `
        -Port $rconPort `
        -Command "execute in minecraft:overworld run tessera-console-context rcon-dimension"
    if ($rconDimensionResponse -notmatch "TESSERA_CONSOLE_CONTEXT_OK") {
        throw "RCON dimension command returned an unexpected response: $rconDimensionResponse"
    }
    $rconSelectorResponse = Invoke-RconCommand `
        -Port $rconPort `
        -Command "execute in minecraft:overworld positioned 736 80 736 if entity @e[type=minecraft:armor_stand,distance=..8] run tessera-console-context rcon-selector"
    if ($rconSelectorResponse -notmatch "TESSERA_CONSOLE_CONTEXT_OK.*rcon-selector") {
        throw "RCON local selector returned an unexpected response: $rconSelectorResponse"
    }
    $rconBlockResponses = @(
        (Invoke-RconCommand -Port $rconPort -Command "execute in minecraft:overworld if block 15 30 15 minecraft:stone run tessera-console-context rcon-block-if"),
        (Invoke-RconCommand -Port $rconPort -Command "execute in minecraft:overworld unless block 15 30 15 minecraft:barrier run tessera-console-context rcon-block-unless"),
        (Invoke-RconCommand -Port $rconPort -Command "execute in minecraft:overworld if block 15 30 15 #minecraft:mineable/pickaxe run tessera-console-context rcon-block-tag"),
        (Invoke-RconCommand -Port $rconPort -Command "execute in minecraft:the_nether if block 736 80 736 minecraft:stone run tessera-console-context rcon-block-nether"),
        (Invoke-RconCommand -Port $rconPort -Command "execute in minecraft:the_end if block 736 80 736 minecraft:stone run tessera-console-context rcon-block-end")
    )
    foreach ($index in 0..($rconBlockResponses.Count - 1)) {
        if ($rconBlockResponses[$index] -notmatch "TESSERA_CONSOLE_CONTEXT_OK") {
            throw "RCON block command $index returned an unexpected response: $($rconBlockResponses[$index])"
        }
    }
    $rconBlockDataResponses = @(
        (Invoke-RconCommand -Port $rconPort -Command "execute in minecraft:overworld run data get block 16 30 15 id"),
        (Invoke-RconCommand -Port $rconPort -Command "execute in minecraft:the_nether run data get block 737 80 736 id"),
        (Invoke-RconCommand -Port $rconPort -Command "execute in minecraft:the_end run data get block 737 80 736 id")
    )
    foreach ($index in 0..($rconBlockDataResponses.Count - 1)) {
        if ($rconBlockDataResponses[$index] -notmatch "minecraft:chest") {
            throw "RCON block data-get $index returned an unexpected response: $($rconBlockDataResponses[$index])"
        }
    }
    $rconLimitedDataResponse = Invoke-RconCommand `
        -Port $rconPort `
        -Command "execute in minecraft:overworld positioned 736 80 736 run data get entity @e[type=minecraft:armor_stand,limit=1]"
    if ($rconLimitedDataResponse -notmatch "following entity data|Unbounded entity selectors|Incorrect argument for command") {
        throw "RCON limited data-get selector returned an unexpected response: $rconLimitedDataResponse"
    }
    $rconUnboundedResponse = Invoke-RconCommand `
        -Port $rconPort `
        -Command "execute in minecraft:overworld positioned 736 80 736 as @e[type=minecraft:armor_stand] run tessera-console-context rcon-selector-unbounded"
    if ($rconUnboundedResponse -notmatch "TESSERA_CONSOLE_CONTEXT_OK.*rcon-selector-unbounded") {
        throw "RCON unbounded selector returned an unexpected response: $rconUnboundedResponse"
    }
    $rconCrossRegionResponse = Invoke-RconCommand `
        -Port $rconPort `
        -Command "execute in minecraft:overworld positioned 736 80 736 if entity @e[type=minecraft:armor_stand,distance=..1024] run say unsafe-cross-region"
    if ($rconCrossRegionResponse -notmatch "crosses region boundaries") {
        throw "RCON cross-region selector was not rejected clearly: $rconCrossRegionResponse"
    }
    $rconUnloadedResponse = Invoke-RconCommand `
        -Port $rconPort `
        -Command "execute in minecraft:the_end positioned 50000 80 50000 if entity @e[distance=..8] run say unloaded"
    if ($rconUnloadedResponse -notmatch "is not loaded") {
        throw "RCON unloaded selector target was not rejected clearly: $rconUnloadedResponse"
    }
    $rconUnloadedBlockResponse = Invoke-RconCommand `
        -Port $rconPort `
        -Command "execute in minecraft:the_end if block 50000 80 50000 minecraft:stone run say unloaded-block"
    if ($rconUnloadedBlockResponse -notmatch "is not loaded") {
        throw "RCON unloaded block target was not rejected clearly: $rconUnloadedBlockResponse"
    }

    $interactive.Process.StandardInput.WriteLine(
        "execute in minecraft:overworld positioned 736 80 736 run kill @e[type=minecraft:armor_stand,distance=..8]"
    )
    $interactive.Process.StandardInput.WriteLine(
        "execute in minecraft:overworld positioned 736 80 736 unless entity @e[type=minecraft:armor_stand,distance=..8] run tessera-console-context selector-kill-empty"
    )
    $interactive.Process.StandardInput.Flush()
    Wait-LogPattern `
        -Server $interactive `
        -Pattern "TESSERA_CONSOLE_CONTEXT_OK.*selector-kill-empty" `
        -TimeoutSeconds $CommandTimeoutSeconds | Out-Null
    Wait-LogPattern `
        -Server $interactive `
        -Pattern "TESSERA_CONSOLE_CONTEXT_OK.*rcon-dimension" `
        -TimeoutSeconds $CommandTimeoutSeconds | Out-Null
    Assert-NoCommandContextFailure -Server $interactive
} finally {
    Stop-SmokeProcess -Server $interactive
}

Write-Host "PASS: startup-buffered console, entity selectors, regional block reads, explicit dimensions, RCON, and stop commands succeeded."
