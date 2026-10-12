param(
    [Parameter(Mandatory)][string]$Variant,
    [Parameter(Mandatory)][string]$BundlerPath,
    [string]$ReferenceBundlerPath,
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidateRange(0,8)][int]$ComputeWorkers = 0,
    [int]$Repetition = 1,
    [int]$WarmupTicks = 120,
    [int]$MeasuredTicks = 200,
    [switch]$VerifyAllSensors,
    [switch]$Adaptive,
    [switch]$Reuse,
    [switch]$WorkTimings,
    [switch]$BrainScratch,
    [switch]$LocalConditions,
    [switch]$PoiScratch,
    [switch]$ReservationCheck
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) { throw 'Set -JavaHome to JDK 25' }
if (-not $ReferenceBundlerPath) { $ReferenceBundlerPath = $BundlerPath }
$taskLabRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$taskJavaHome = ($JavaHome)
$taskJava = Join-Path $taskJavaHome 'bin\java.exe'
$taskBuild = Join-Path $taskLabRoot 'build\tessera-x-lab\sensor-comparison\plugin'
$taskFixture = Join-Path $taskLabRoot 'experiments\tessera-x\sensor-pilot'
$taskTemp = Join-Path $taskLabRoot 'build\tessera-x-lab\native-temp'
$taskBundler = (Resolve-Path -LiteralPath $BundlerPath).Path
New-Item -ItemType Directory -Path $taskBuild, $taskTemp -Force | Out-Null
$env:TEMP = $taskTemp; $env:TMP = $taskTemp
$taskDependencies = Join-Path $taskBuild 'dependencies'
$taskPlugin = Join-Path $taskBuild 'sensor-comparison.jar'
$taskStamp = Join-Path $taskBuild 'source-hash.txt'
$taskSourceHash = (Get-FileHash -LiteralPath (Join-Path $taskFixture 'SensorComparison.java') -Algorithm SHA256).Hash
if (-not (Test-Path -LiteralPath $taskPlugin) -or -not (Test-Path -LiteralPath $taskStamp) -or
    (Get-Content -LiteralPath $taskStamp -Raw).Trim() -ne $taskSourceHash) {
$taskClasses = Join-Path $taskBuild ('classes-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $taskDependencies, $taskClasses -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
# The fixture always compiles against the original 019. One binary works for both servers.
$taskReference = (Resolve-Path -LiteralPath $ReferenceBundlerPath).Path
$taskZip = [IO.Compression.ZipFile]::OpenRead($taskReference)
try {
    foreach ($taskEntry in $taskZip.Entries) {
        if ($taskEntry.FullName.StartsWith('META-INF/') -and $taskEntry.FullName.EndsWith('.jar')) {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($taskEntry, (Join-Path $taskDependencies $taskEntry.Name), $true)
        }
    }
} finally { $taskZip.Dispose() }
$taskClasspath = (Get-ChildItem -LiteralPath $taskDependencies -Filter '*.jar' | ForEach-Object { $_.FullName.Replace('\','/') }) -join ';'
$taskArgsFile = Join-Path $taskBuild 'javac.args'
@('--release','25','-encoding','UTF-8','-classpath',('"' + $taskClasspath + '"'),'-d',
    ('"' + $taskClasses.Replace('\','/') + '"'),('"' + (Join-Path $taskFixture 'SensorComparison.java').Replace('\','/') + '"')) |
    Set-Content -LiteralPath $taskArgsFile -Encoding utf8
& (Join-Path $taskJavaHome 'bin\javac.exe') ('@' + $taskArgsFile)
if ($LASTEXITCODE -ne 0) { throw 'Comparison fixture compilation failed' }
Copy-Item -LiteralPath (Join-Path $taskFixture 'plugin.yml') -Destination $taskClasses
$taskPlugin = Join-Path $taskBuild 'sensor-comparison.jar'
& (Join-Path $taskJavaHome 'bin\jar.exe') --create --file $taskPlugin -C $taskClasses .
if ($LASTEXITCODE -ne 0) { throw 'Comparison fixture packaging failed' }
Set-Content -LiteralPath $taskStamp -Value $taskSourceHash -Encoding ascii
}
$taskRun = Join-Path $taskLabRoot ('build\tessera-x-lab\sensor-comparison\runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + $Variant + '-w' + $ComputeWorkers + '-r' + $Repetition)
New-Item -ItemType Directory -Path (Join-Path $taskRun 'plugins'), (Join-Path $taskRun 'config') -Force | Out-Null
Copy-Item -LiteralPath $taskPlugin -Destination (Join-Path $taskRun 'plugins')
Set-Content -LiteralPath (Join-Path $taskRun 'eula.txt') -Value 'eula=true' -Encoding ascii
@('server-ip=127.0.0.1','server-port=0','online-mode=false','view-distance=2','simulation-distance=2',
    'spawn-protection=0','level-type=minecraft:flat','level-seed=829104','generate-structures=false',
    'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}',
    'sync-chunk-writes=false','pause-when-empty-seconds=-1','enable-rcon=false') |
    Set-Content -LiteralPath (Join-Path $taskRun 'server.properties') -Encoding ascii
@('_version: 31','threaded-regions:','  threads: 4','  grid-exponent: 4','chunk-system:',
    '  worker-threads: 2','  io-threads: 1','spark:','  enabled: false','update-checker:','  enabled: false') |
    Set-Content -LiteralPath (Join-Path $taskRun 'config\paper-global.yml') -Encoding ascii
$taskFeature = if ($Variant -eq 'tessera') { 'false' } else { 'true' }
$taskVerify = if ($VerifyAllSensors) { 'true' } else { 'false' }
$taskAdaptive = if ($Adaptive) { 'true' } else { 'false' }
$taskReuse = if ($Reuse) { 'true' } else { 'false' }
$taskTimings = if ($WorkTimings) { 'true' } else { 'false' }
$taskBrain = if ($BrainScratch) { 'true' } else { 'false' }
$taskConditions = if ($LocalConditions) { 'true' } else { 'false' }
$taskPoi = if ($PoiScratch) { 'true' } else { 'false' }
$taskReservation = if ($ReservationCheck) { 'true' } else { 'false' }
$taskOptions = "-Xms1G -Xmx3G -XX:ActiveProcessorCount=12 -Dfile.encoding=UTF-8 -Dlab.warmup-ticks=$WarmupTicks -Dlab.measured-ticks=$MeasuredTicks -Dtessera-x.sensors=$taskFeature -Dtessera-x.sensor-workers=$ComputeWorkers -Dtessera-x.verify-sensors=$taskVerify -Dtessera-x.sensor-adaptive=$taskAdaptive -Dtessera-x.sensor-reuse=$taskReuse -Dtessera-x.work-timings=$taskTimings -Dtessera-x.brain-scratch=$taskBrain -Dtessera-x.sensor-local-conditions=$taskConditions -Dtessera-x.poi-scratch=$taskPoi -Dtessera-x.poi-reservation-check=$taskReservation"
$taskInvocation = [ordered]@{ variant=$Variant; computeWorkers=$ComputeWorkers; repetition=$Repetition; warmupTicks=$WarmupTicks;
    measuredTicks=$MeasuredTicks; verifyAllSensors=[bool]$VerifyAllSensors; adaptive=[bool]$Adaptive; reuse=[bool]$Reuse;
    workTimings=[bool]$WorkTimings; brainScratch=[bool]$BrainScratch; localConditions=[bool]$LocalConditions;
    poiScratch=[bool]$PoiScratch; reservationCheck=[bool]$ReservationCheck; bundler=$taskBundler;
    bundlerSha256=(Get-FileHash -LiteralPath $taskBundler -Algorithm SHA256).Hash;
    fixtureSha256=(Get-FileHash -LiteralPath (Join-Path $taskFixture 'SensorComparison.java') -Algorithm SHA256).Hash;
    pluginSha256=(Get-FileHash -LiteralPath $taskPlugin -Algorithm SHA256).Hash; javaOptions=$taskOptions;
    startedUtc=[DateTimeOffset]::UtcNow.ToString('o'); productionServerContacted=$false }
$taskInvocation | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskRun 'invocation.json') -Encoding utf8
$taskStart = [Diagnostics.ProcessStartInfo]::new()
$taskStart.FileName=$taskJava; $taskStart.WorkingDirectory=$taskRun; $taskStart.UseShellExecute=$false; $taskStart.CreateNoWindow=$true
$taskStart.RedirectStandardInput=$true; $taskStart.RedirectStandardOutput=$true; $taskStart.RedirectStandardError=$true
$taskStart.Arguments=$taskOptions + ' -jar "' + $taskBundler + '" nogui'
$taskProcess = [Diagnostics.Process]::new(); $taskProcess.StartInfo=$taskStart
Write-Host "Comparison $Variant workers=$ComputeWorkers repetition=$Repetition : $taskRun"
if (-not $taskProcess.Start()) { throw 'Native server did not start' }
$taskStdout=$taskProcess.StandardOutput.ReadToEndAsync(); $taskStderr=$taskProcess.StandardError.ReadToEndAsync()
$taskTimer=[Diagnostics.Stopwatch]::StartNew()
try {
    while (-not $taskProcess.WaitForExit(1000)) {
        if ($taskTimer.Elapsed.TotalSeconds -ge 480) { throw 'Comparison timed out' }
        if ([int]$taskTimer.Elapsed.TotalSeconds % 20 -eq 0) {
            $taskLog = Join-Path $taskRun 'logs\latest.log'
            if (Test-Path -LiteralPath $taskLog) {
                Get-Content -LiteralPath $taskLog -Tail 2 | Write-Host
            }
        }
    }
} finally {
    if (-not $taskProcess.HasExited) {
        $taskProcess.StandardInput.WriteLine('stop'); $taskProcess.StandardInput.Flush()
        if (-not $taskProcess.WaitForExit(30000)) { $taskProcess.Kill(); $taskProcess.WaitForExit() }
    }
    [IO.File]::WriteAllText((Join-Path $taskRun 'stdout.log'), $taskStdout.GetAwaiter().GetResult())
    [IO.File]::WriteAllText((Join-Path $taskRun 'stderr.log'), $taskStderr.GetAwaiter().GetResult())
}
$taskResultPath = Join-Path $taskRun 'comparison-result.json'
if (-not (Test-Path -LiteralPath $taskResultPath)) { throw "No comparison result: $taskRun" }
$taskResult = Get-Content -LiteralPath $taskResultPath -Raw | ConvertFrom-Json
$taskErrors = @(Select-String -LiteralPath (Join-Path $taskRun 'logs\latest.log') -Pattern '/(?:ERROR|SEVERE)\]:')
$taskKnown = @($taskErrors | Where-Object { $_.Line -match '\[CrashReport preload thread/(?:ERROR|SEVERE)\].*(?:HkeyPerformanceDataUtil|Thread failed main thread check: Async chunk retrieval)' })
$taskUnexpected = @($taskErrors | Where-Object { $_ -notin $taskKnown })
[ordered]@{ serverExitCode=$taskProcess.ExitCode; knownBaselineDiagnostics=@($taskKnown | ForEach-Object {$_.Line});
    unexpectedErrors=@($taskUnexpected | ForEach-Object {$_.Line}); elapsedSeconds=$taskTimer.Elapsed.TotalSeconds } |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $taskRun 'log-validation.json') -Encoding utf8
if ($taskResult.status -ne 'PASS' -or $taskProcess.ExitCode -ne 0 -or $taskUnexpected.Count) {
    throw "Comparison failed: $($taskResult.failure); exit=$($taskProcess.ExitCode); unexpected errors=$($taskUnexpected.Count)"
}
Write-Host "PASS: $taskResultPath"
