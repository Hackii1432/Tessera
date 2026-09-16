param(
    [string]$BundlerPath,
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidateRange(60, 900)][int]$TimeoutSeconds = 600,
    [ValidateRange(1, 8)][int]$Repetitions = 1
)

$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
if (-not $BundlerPath) {
    $BundlerPath = Join-Path $repository 'folia-server\build\libs\folia-bundler-26.3.build.005-alpha.jar'
}
$BundlerPath = (Resolve-Path -LiteralPath $BundlerPath).Path
if (-not $JavaHome) {
    $JavaHome = Split-Path (Split-Path (Get-Command javac -ErrorAction Stop).Source)
}
$java = Join-Path $JavaHome 'bin\java.exe'
$javac = Join-Path $JavaHome 'bin\javac.exe'
$jar = Join-Path $JavaHome 'bin\jar.exe'
foreach ($executable in @($java, $javac, $jar)) {
    if (-not (Test-Path -LiteralPath $executable)) { throw "JDK executable missing: $executable" }
}
$build = Join-Path $PSScriptRoot 'build'
$dependencies = Join-Path $build 'dependencies'
$classes = Join-Path $build ('classes-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $dependencies, $classes -Force | Out-Null

# The assembled bundler contains the exact server and dependency JARs. No downloads or Gradle writes needed.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($BundlerPath)
try {
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName.EndsWith('.jar') -and $entry.FullName.StartsWith('META-INF/')) {
            $destination = Join-Path $dependencies $entry.Name
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destination, $true)
        }
    }
} finally { $zip.Dispose() }
$argumentsFile = Join-Path $build 'javac.args'
$classpath = (Get-ChildItem -LiteralPath $dependencies -Filter '*.jar' | ForEach-Object { $_.FullName.Replace('\', '/') }) -join ';'
@(
    '--release', '25', '-encoding', 'UTF-8', '-classpath',
    ('"' + $classpath + '"'),
    '-d', ('"' + $classes.Replace('\', '/') + '"'),
    ('"' + (Join-Path $PSScriptRoot 'RedstoneRegionMergeSmoke.java').Replace('\', '/') + '"')
) | Set-Content -LiteralPath $argumentsFile -Encoding utf8
& $javac ('@' + $argumentsFile)
if ($LASTEXITCODE -ne 0) { throw 'Smoke plugin compilation failed' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'plugin.yml') -Destination $classes
$plugin = Join-Path $build 'redstone-region-merge-smoke.jar'
& $jar --create --file $plugin -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Smoke plugin packaging failed' }

for ($iteration = 1; $iteration -le $Repetitions; $iteration++) {
    $run = Join-Path $build ('runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + $iteration)
    New-Item -ItemType Directory -Path (Join-Path $run 'plugins'), (Join-Path $run 'config') -Force | Out-Null
    Copy-Item -LiteralPath $plugin -Destination (Join-Path $run 'plugins')
    [ordered]@{
        bundler = $BundlerPath
        bundlerSha256 = (Get-FileHash -LiteralPath $BundlerPath -Algorithm SHA256).Hash
        pluginSha256 = (Get-FileHash -LiteralPath $plugin -Algorithm SHA256).Hash
        sourceSha256 = (Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'RedstoneRegionMergeSmoke.java') -Algorithm SHA256).Hash
        javaHome = $JavaHome
        iteration = $iteration
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $run 'invocation.json') -Encoding utf8
    Set-Content -LiteralPath (Join-Path $run 'eula.txt') -Value 'eula=true' -Encoding ascii
    @(
        'server-ip=127.0.0.1', 'server-port=0', 'online-mode=false',
        'view-distance=2', 'simulation-distance=2', 'spawn-protection=0',
        'level-type=minecraft:flat', 'level-seed=829104', 'generate-structures=false',
        'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}',
        'sync-chunk-writes=false', 'pause-when-empty-seconds=-1', 'enable-rcon=false'
    ) | Set-Content -LiteralPath (Join-Path $run 'server.properties') -Encoding ascii
    @('_version: 31', 'threaded-regions:', '  threads: 4', '  grid-exponent: 4',
      'spark:', '  enabled: false', 'update-checker:', '  enabled: false') |
        Set-Content -LiteralPath (Join-Path $run 'config\paper-global.yml') -Encoding ascii
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $java
    $start.WorkingDirectory = $run
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.Arguments = '-Xms512M -Xmx3G -Dfile.encoding=UTF-8 -jar "' + $BundlerPath + '" nogui'
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    Write-Host "Redstone region merge run $iteration/$Repetitions : $run"
    if (-not $process.Start()) { throw 'Server did not start' }
    $stdout = $process.StandardOutput.ReadToEndAsync()
    $stderr = $process.StandardError.ReadToEndAsync()
    $timer = [Diagnostics.Stopwatch]::StartNew()
    try {
        while (-not $process.WaitForExit(1000)) {
            if ($timer.Elapsed.TotalSeconds -ge $TimeoutSeconds) { throw 'Redstone merge smoke timed out' }
            if ([int]$timer.Elapsed.TotalSeconds % 15 -eq 0) {
                $log = Join-Path $run 'logs\latest.log'
                if (Test-Path -LiteralPath $log) {
                    Write-Host ((Get-Content -LiteralPath $log -Tail 3) -join [Environment]::NewLine)
                }
            }
        }
    } finally {
        if (-not $process.HasExited) {
            $process.StandardInput.WriteLine('stop')
            $process.StandardInput.Flush()
            if (-not $process.WaitForExit(30000)) { $process.Kill(); $process.WaitForExit() }
        }
        [IO.File]::WriteAllText((Join-Path $run 'stdout.log'), $stdout.GetAwaiter().GetResult())
        [IO.File]::WriteAllText((Join-Path $run 'stderr.log'), $stderr.GetAwaiter().GetResult())
    }
    $resultPath = Join-Path $run 'redstone-merge-result.json'
    if (-not (Test-Path -LiteralPath $resultPath)) {
        Get-Content -LiteralPath (Join-Path $run 'stdout.log') -Tail 70
        Get-Content -LiteralPath (Join-Path $run 'stderr.log') -Tail 30
        throw "No test result produced: $run"
    }
    $result = Get-Content -LiteralPath $resultPath -Raw | ConvertFrom-Json
    if ($result.status -ne 'PASS' -or $process.ExitCode -ne 0) {
        throw "Redstone merge smoke failed: $($result.failure); exit=$($process.ExitCode); result=$resultPath"
    }
    $logPath = Join-Path $run 'logs\latest.log'
    $knownHostDiagnostic = '\[CrashReport preload thread/ERROR\]: \[oshi\.driver\.windows\.registry\.HkeyPerformanceDataUtil\] Unable to locate English counter names in registry Perflib 009\.'
    $errorLines = @(Select-String -LiteralPath $logPath -Pattern '/(?:ERROR|SEVERE)\]:')
    $hostDiagnostics = @($errorLines | Where-Object { $_.Line -match $knownHostDiagnostic })
    $unexpectedErrors = @($errorLines | Where-Object { $_.Line -notmatch $knownHostDiagnostic })
    $unsafe = @(Select-String -LiteralPath $logPath -Pattern @(
        'Thread failed main thread check', 'Cannot .* asynchronously', 'ConcurrentModificationException',
        'Watchdog.*stopping', 'Exception ticking'
    )) + $unexpectedErrors
    [ordered]@{
        serverExitCode = $process.ExitCode
        unexpectedErrors = @($unsafe | ForEach-Object { $_.Line })
        hostPerformanceCounterDiagnostics = @($hostDiagnostics | ForEach-Object { $_.Line })
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $run 'log-validation.json') -Encoding utf8
    if ($unsafe.Count -gt 0) { throw "Server log contains errors: $($unsafe.Line -join '; ')" }
    if ($hostDiagnostics.Count -gt 0) {
        Write-Host 'Recorded the Windows OSHI performance-counter startup diagnostic in log-validation.json.'
    }
    Write-Host "PASS: $resultPath"
}
