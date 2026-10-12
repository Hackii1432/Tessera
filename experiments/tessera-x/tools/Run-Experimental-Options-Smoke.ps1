param(
    [ValidateSet('recommended','all-off','master-off','all-on','default-generated')][string]$Mode = 'recommended',
    [Parameter(Mandatory)][string]$BundlerPath,
    [string]$ReferenceBundlerPath,
    [string]$JavaHome = $env:JAVA_HOME

)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) { throw 'Set -JavaHome to JDK 25' }
if (-not $ReferenceBundlerPath) { $ReferenceBundlerPath = $BundlerPath }
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$taskJdk = ($JavaHome)
$taskBuild = Join-Path $taskRoot 'build\tessera-x-lab\experimental-options\plugin'
$taskDependencies = Join-Path $taskBuild 'dependencies'
$taskClasses = Join-Path $taskBuild ('classes-' + [Guid]::NewGuid().ToString('N'))
$taskFixture = Join-Path $taskRoot 'experiments\tessera-x\config'
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
    ('"' + $taskClasses.Replace('\','/') + '"'),('"' + (Join-Path $taskFixture 'ExperimentalOptionsSmoke.java').Replace('\','/') + '"')) |
    Set-Content -LiteralPath $taskArguments -Encoding utf8
& (Join-Path $taskJdk 'bin\javac.exe') ('@' + $taskArguments)
if ($LASTEXITCODE -ne 0) { throw 'Experimental options fixture compilation failed' }
Copy-Item -LiteralPath (Join-Path $taskFixture 'plugin.yml') -Destination $taskClasses
$taskPlugin = Join-Path $taskBuild 'experimental-options.jar'
& (Join-Path $taskJdk 'bin\jar.exe') --create --file $taskPlugin -C $taskClasses .
if ($LASTEXITCODE -ne 0) { throw 'Experimental options fixture packaging failed' }
$taskRun = Join-Path $taskRoot ('build\tessera-x-lab\experimental-options\runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + $Mode)
New-Item -ItemType Directory -Path (Join-Path $taskRun 'plugins'), (Join-Path $taskRun 'config') -Force | Out-Null
Copy-Item -LiteralPath $taskPlugin -Destination (Join-Path $taskRun 'plugins')
Set-Content -LiteralPath (Join-Path $taskRun 'eula.txt') -Value 'eula=true' -Encoding ascii
@('server-ip=127.0.0.1','server-port=0','online-mode=false','view-distance=2','simulation-distance=2',
    'spawn-protection=0','level-type=minecraft:flat','level-seed=829104','generate-structures=false',
    'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}',
    'sync-chunk-writes=false','pause-when-empty-seconds=-1','enable-rcon=false') |
    Set-Content -LiteralPath (Join-Path $taskRun 'server.properties') -Encoding ascii
@('_version: 31','threaded-regions:','  threads: 4','  grid-exponent: 1','chunk-system:',
    '  worker-threads: 2','  io-threads: 1','spark:','  enabled: false','update-checker:','  enabled: false') |
    Set-Content -LiteralPath (Join-Path $taskRun 'config\paper-global.yml') -Encoding ascii
$taskFeatureKeys=@('sensor-sorting','sensor-adaptive','sensor-reuse','verify-sensors','region-recalculation','region-covered-search','brain-scratch','sensor-local-conditions','poi-scratch','poi-reservation-check','work-timings')
$taskYaml=Get-Content -LiteralPath (Join-Path $taskRoot 'experiments\tessera-x\config\tessera-x-recommended.yml') -Raw
if($Mode -eq 'all-off') { foreach($taskKey in $taskFeatureKeys){$taskYaml=[regex]::Replace($taskYaml,'(?m)^  '+[regex]::Escape($taskKey)+':[^\r\n]*','  '+$taskKey+': false')} }
if($Mode -in @('all-on','master-off')) { foreach($taskKey in $taskFeatureKeys){$taskYaml=[regex]::Replace($taskYaml,'(?m)^  '+[regex]::Escape($taskKey)+':[^\r\n]*','  '+$taskKey+': true')} }
if($Mode -eq 'master-off') {$taskYaml=[regex]::Replace($taskYaml,'(?m)^  enabled:[^\r\n]*','  enabled: false')}
$taskConfigDir=if($Mode -eq 'recommended'){'settings-alt'}else{'config'}
New-Item -ItemType Directory -Path (Join-Path $taskRun $taskConfigDir) -Force | Out-Null
if($taskConfigDir -ne 'config'){Copy-Item -LiteralPath (Join-Path $taskRun 'config\paper-global.yml') -Destination (Join-Path $taskRun ($taskConfigDir+'\paper-global.yml'))}
$taskConfigFile=$taskConfigDir+'/tessera-x.yml'
if($Mode -ne 'default-generated'){Set-Content -LiteralPath (Join-Path $taskRun $taskConfigFile) -Value $taskYaml -Encoding utf8}
$taskLegacy=if($Mode -in @('all-off','master-off')){'true'}else{'false'}
$taskDelay=if($taskLegacy -eq 'true'){1000}else{0}
$taskLegacyWorkers=if($taskLegacy -eq 'true'){8}else{0}
$taskOptions="-Xms1G -Xmx3G -XX:ActiveProcessorCount=12 -Dfile.encoding=UTF-8 -Dlab.config-mode=$Mode -Dlab.config-file=$taskConfigFile -Dtessera-x.sensors=$taskLegacy -Dtessera-x.sensor-workers=$taskLegacyWorkers -Dtessera-x.sensor-adaptive=$taskLegacy -Dtessera-x.sensor-reuse=$taskLegacy -Dtessera-x.verify-sensors=$taskLegacy -Dtessera-x.region-recalc-max-delay-ms=$taskDelay -Dtessera-x.region-covered-search=$taskLegacy -Dtessera-x.brain-scratch=$taskLegacy -Dtessera-x.sensor-local-conditions=$taskLegacy -Dtessera-x.poi-scratch=$taskLegacy -Dtessera-x.poi-reservation-check=$taskLegacy -Dtessera-x.work-timings=$taskLegacy"
if($Mode -eq 'default-generated'){$taskOptions="-Xms1G -Xmx3G -XX:ActiveProcessorCount=12 -Dfile.encoding=UTF-8 -Dlab.config-mode=$Mode -Dlab.config-file=$taskConfigFile"}
[ordered]@{ variant=$Mode; configMode=$Mode; yamlSha256=$(if(Test-Path -LiteralPath (Join-Path $taskRun $taskConfigFile)){(Get-FileHash -LiteralPath (Join-Path $taskRun $taskConfigFile)).Hash}else{$null}); bundler=$taskBundler;
    bundlerSha256=(Get-FileHash -LiteralPath $taskBundler -Algorithm SHA256).Hash;
    fixtureSha256=(Get-FileHash -LiteralPath (Join-Path $taskFixture 'ExperimentalOptionsSmoke.java') -Algorithm SHA256).Hash;
    pluginSha256=(Get-FileHash -LiteralPath $taskPlugin -Algorithm SHA256).Hash;
    javaOptions=$taskOptions; productionServerContacted=$false; startedUtc=[DateTimeOffset]::UtcNow.ToString('o') } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRun 'invocation.json') -Encoding utf8
$taskStart = [Diagnostics.ProcessStartInfo]::new()
$taskStart.FileName = Join-Path $taskJdk 'bin\java.exe'; $taskStart.WorkingDirectory=$taskRun
$taskStart.UseShellExecute=$false; $taskStart.CreateNoWindow=$true
$taskStart.RedirectStandardInput=$true; $taskStart.RedirectStandardOutput=$true; $taskStart.RedirectStandardError=$true
$taskStart.Arguments=$taskOptions + ' -jar "' + $taskBundler + '" --paper-dir '+$taskConfigDir+' nogui'
$taskProcess = [Diagnostics.Process]::new(); $taskProcess.StartInfo=$taskStart
Write-Host "Experimental options $Mode mode=$Mode : $taskRun"
if (-not $taskProcess.Start()) { throw 'Native server did not start' }
$taskStdout=$taskProcess.StandardOutput.ReadToEndAsync(); $taskStderr=$taskProcess.StandardError.ReadToEndAsync()
$taskTimer=[Diagnostics.Stopwatch]::StartNew()
try {
    while (-not $taskProcess.WaitForExit(1000)) {
        if ($taskTimer.Elapsed.TotalSeconds -ge 360) { throw 'Experimental options timed out' }
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
$taskResultPath=Join-Path $taskRun 'config-smoke-result.json'
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
    throw "Experimental options failed: $($taskResult.failure); exit=$($taskProcess.ExitCode); unexpected=$($taskUnexpected.Count)"
}
Write-Host "PASS: $taskResultPath"
