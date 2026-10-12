param(
    [Parameter(Mandatory)][string]$Variant,
    [Parameter(Mandatory)][string]$BundlerPath,
    [string]$ReferenceBundlerPath,
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidateSet(0,1000)][int]$DelayMs = 0,
    [switch]$CoveredSearch
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))) { throw 'Set -JavaHome to JDK 25' }
if (-not $ReferenceBundlerPath) { $ReferenceBundlerPath = $BundlerPath }
$taskRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$taskJdk = ($JavaHome)
$taskBuild = Join-Path $taskRoot 'build\tessera-x-lab\region-split\fixture'
$taskRun = Join-Path $taskRoot ('build\tessera-x-lab\region-split\runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + $Variant)
$taskDependencies = Join-Path $taskRun 'dependencies'
$taskClasses = Join-Path $taskBuild 'classes'
New-Item -ItemType Directory -Path $taskDependencies, $taskClasses -Force | Out-Null
$taskTemp = Join-Path $taskRoot 'build\tessera-x-lab\native-temp'
New-Item -ItemType Directory -Path $taskTemp -Force | Out-Null
$env:TEMP = $taskTemp; $env:TMP = $taskTemp
$taskBundler = (Resolve-Path -LiteralPath $BundlerPath).Path
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskZip = [IO.Compression.ZipFile]::OpenRead($taskBundler)
try {
    foreach ($taskEntry in $taskZip.Entries) {
        if ($taskEntry.FullName.StartsWith('META-INF/') -and $taskEntry.FullName.EndsWith('.jar')) {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($taskEntry, (Join-Path $taskDependencies $taskEntry.Name), $true)
        }
    }
} finally { $taskZip.Dispose() }
$taskClasspath = (Get-ChildItem -LiteralPath $taskDependencies -Filter '*.jar' | ForEach-Object { $_.FullName.Replace('\','/') }) -join ';'
$taskFixture = Join-Path $taskRoot 'experiments\tessera-x\region-split\RegionSplitComparison.java'
$taskArguments = Join-Path $taskBuild 'javac.args'
@('--release','25','-encoding','UTF-8','-classpath',('"' + $taskClasspath + '"'),'-d',
    ('"' + $taskClasses.Replace('\','/') + '"'),('"' + $taskFixture.Replace('\','/') + '"')) |
    Set-Content -LiteralPath $taskArguments -Encoding utf8
& (Join-Path $taskJdk 'bin\javac.exe') ('@' + $taskArguments)
if ($LASTEXITCODE -ne 0) { throw 'Region split fixture compilation failed' }
$taskResult = Join-Path $taskRun 'result.json'
[ordered]@{ variant=$Variant; delayMs=$DelayMs; coveredSearch=[bool]$CoveredSearch; bundler=$taskBundler;
    bundlerSha256=(Get-FileHash -LiteralPath $taskBundler -Algorithm SHA256).Hash;
    fixtureSha256=(Get-FileHash -LiteralPath $taskFixture -Algorithm SHA256).Hash;
    productionServerContacted=$false; startedUtc=[DateTimeOffset]::UtcNow.ToString('o') } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskRun 'invocation.json') -Encoding utf8
$taskRuntimeClasspath = $taskClasses.Replace('\','/') + ';' + $taskClasspath
$taskCovered = if ($CoveredSearch) { 'true' } else { 'false' }
& (Join-Path $taskJdk 'bin\java.exe') '-Xms128M' '-Xmx1G' "-Dtessera-x.region-recalc-max-delay-ms=$DelayMs" "-Dtessera-x.region-covered-search=$taskCovered" '-cp' $taskRuntimeClasspath 'RegionSplitComparison' $taskResult *> (Join-Path $taskRun 'run.log')
if ($LASTEXITCODE -ne 0) { Get-Content -LiteralPath (Join-Path $taskRun 'run.log') -Tail 20; throw 'Region split fixture failed' }
Get-Content -LiteralPath $taskResult
