param(
    [Parameter(Mandatory)][string]$Variant,
    [Parameter(Mandatory)][string]$BundlerPath,
    [string]$ReferenceBundlerPath,
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$PoiScratch,
    [switch]$ReservationCheck
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) { throw 'Set -JavaHome to JDK 25' }
if (-not $ReferenceBundlerPath) { $ReferenceBundlerPath = $BundlerPath }
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$taskJdk = ($JavaHome)
$taskBuild = Join-Path $taskRoot 'build\tessera-x-lab\poi-safety\plugin'
$taskDependencies = Join-Path $taskBuild 'dependencies'
$taskClasses = Join-Path $taskBuild ('classes-' + [Guid]::NewGuid().ToString('N'))
$taskFixture = Join-Path $taskRoot 'experiments\tessera-x\poi-safety'
$taskBundler = (Resolve-Path -LiteralPath $BundlerPath).Path
New-Item -ItemType Directory -Path $taskDependencies, $taskClasses -Force | Out-Null
$taskTemp = Join-Path $taskRoot 'build\tessera-x-lab\native-temp'
New-Item -ItemType Directory -Path $taskTemp -Force | Out-Null
$env:TEMP = $taskTemp; $env:TMP = $taskTemp
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskZip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ReferenceBundlerPath).Path)
try {
    foreach ($taskEntry in $taskZip.Entries) {
        if ($taskEntry.FullName.StartsWith('META-INF/') -and $taskEntry.FullName.EndsWith('.jar')) {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($taskEntry, (Join-Path $taskDependencies $taskEntry.Name), $true)
        }
    }
} finally { $taskZip.Dispose() }
$taskClasspath = (Get-ChildItem -LiteralPath $taskDependencies -Filter '*.jar' | ForEach-Object { $_.FullName.Replace('\','/') }) -join ';'
$taskArguments = Join-Path $taskBuild 'javac.args'
@('--release','25','-encoding','UTF-8','-classpath',('"' + $taskClasspath + '"'),'-d',
    ('"' + $taskClasses.Replace('\','/') + '"'),('"' + (Join-Path $taskFixture 'PoiSafety.java').Replace('\','/') + '"')) |
    Set-Content -LiteralPath $taskArguments -Encoding utf8
& (Join-Path $taskJdk 'bin\javac.exe') ('@' + $taskArguments)
if ($LASTEXITCODE -ne 0) { throw 'POI safety fixture compilation failed' }
Copy-Item -LiteralPath (Join-Path $taskFixture 'plugin.yml') -Destination $taskClasses
$taskPlugin = Join-Path $taskBuild 'poi-safety.jar'
& (Join-Path $taskJdk 'bin\jar.exe') --create --file $taskPlugin -C $taskClasses .
if ($LASTEXITCODE -ne 0) { throw 'POI safety fixture packaging failed' }
$taskRun = Join-Path $taskRoot ('build\tessera-x-lab\poi-safety\runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + $Variant)
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
$taskLocal = if ($ReservationCheck) { 'true' } else { 'false' }
$taskBrain = if ($PoiScratch) { 'true' } else { 'false' }
$taskOptions = "-Xms1G -Xmx3G -XX:ActiveProcessorCount=12 -Dfile.encoding=UTF-8 -Dtessera-x.sensors=false -Dtessera-x.poi-reservation-check=$taskLocal -Dtessera-x.poi-scratch=$taskBrain"
[ordered]@{ variant=$Variant; poiScratch=[bool]$PoiScratch; reservationCheck=[bool]$ReservationCheck; bundler=$taskBundler;
    bundlerSha256=(Get-FileHash -LiteralPath $taskBundler -Algorithm SHA256).Hash;
    fixtureSha256=(Get-FileHash -LiteralPath (Join-Path $taskFixture 'PoiSafety.java') -Algorithm SHA256).Hash;
    pluginSha256=(Get-FileHash -LiteralPath $taskPlugin -Algorithm SHA256).Hash;
    javaOptions=$taskOptions; productionServerContacted=$false; startedUtc=[DateTimeOffset]::UtcNow.ToString('o') } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRun 'invocation.json') -Encoding utf8
$taskStart = [Diagnostics.ProcessStartInfo]::new()
$taskStart.FileName = Join-Path $taskJdk 'bin\java.exe'; $taskStart.WorkingDirectory=$taskRun
$taskStart.UseShellExecute=$false; $taskStart.CreateNoWindow=$true
$taskStart.RedirectStandardInput=$true; $taskStart.RedirectStandardOutput=$true; $taskStart.RedirectStandardError=$true
$taskStart.Arguments=$taskOptions + ' -jar "' + $taskBundler + '" nogui'
$taskProcess = [Diagnostics.Process]::new(); $taskProcess.StartInfo=$taskStart
Write-Host "POI safety $Variant local=$taskLocal scratch=$taskBrain : $taskRun"
if (-not $taskProcess.Start()) { throw 'Native server did not start' }
$taskStdout=$taskProcess.StandardOutput.ReadToEndAsync(); $taskStderr=$taskProcess.StandardError.ReadToEndAsync()
$taskTimer=[Diagnostics.Stopwatch]::StartNew()
try {
    while (-not $taskProcess.WaitForExit(1000)) {
        if ($taskTimer.Elapsed.TotalSeconds -ge 360) { throw 'POI safety timed out' }
        if ([int]$taskTimer.Elapsed.TotalSeconds % 20 -eq 0) {
            $taskLog=Join-Path $taskRun 'logs\latest.log'
            if (Test-Path -LiteralPath $taskLog) { Get-Content -LiteralPath $taskLog -Tail 2 | Write-Host }
        }
    }
} finally {
    if (-not $taskProcess.HasExited) {
        $taskProcess.StandardInput.WriteLine('stop'); $taskProcess.StandardInput.Flush()
        if (-not $taskProcess.WaitForExit(30000)) { $taskProcess.Kill(); $taskProcess.WaitForExit() }
    }
    [IO.File]::WriteAllText((Join-Path $taskRun 'stdout.log'),$taskStdout.GetAwaiter().GetResult())
    [IO.File]::WriteAllText((Join-Path $taskRun 'stderr.log'),$taskStderr.GetAwaiter().GetResult())
}
$taskResultPath=Join-Path $taskRun 'poi-safety-result.json'
if (-not (Test-Path -LiteralPath $taskResultPath)) { throw "No native result: $taskRun" }
$taskResult=Get-Content -LiteralPath $taskResultPath -Raw | ConvertFrom-Json
$taskErrors=@(Select-String -LiteralPath (Join-Path $taskRun 'logs\latest.log') -Pattern '/(?:ERROR|SEVERE)\]:')
$taskKnown=@($taskErrors | Where-Object { $_.Line -match '\[CrashReport preload thread/(?:ERROR|SEVERE)\].*(?:HkeyPerformanceDataUtil|Thread failed main thread check: Async chunk retrieval)' })
$taskUnexpected=@($taskErrors | Where-Object { $_ -notin $taskKnown })
[ordered]@{ serverExitCode=$taskProcess.ExitCode; elapsedSeconds=$taskTimer.Elapsed.TotalSeconds;
    knownBaselineDiagnostics=@($taskKnown | ForEach-Object {$_.Line});
    unexpectedErrors=@($taskUnexpected | ForEach-Object {$_.Line}) } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRun 'log-validation.json') -Encoding utf8
if ($taskResult.status -ne 'PASS' -or $taskProcess.ExitCode -ne 0 -or $taskUnexpected.Count) {
    throw "POI safety failed: $($taskResult.failure); exit=$($taskProcess.ExitCode); unexpected=$($taskUnexpected.Count)"
}
Write-Host "PASS: $taskResultPath"
