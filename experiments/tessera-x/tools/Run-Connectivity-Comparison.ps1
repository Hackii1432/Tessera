param(
    [Parameter(Mandatory)][string]$BundlerPath,
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference='Stop'
if(-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\javac.exe'))){throw 'Set -JavaHome to JDK 25'}
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$taskBuild=Join-Path $taskRoot ('build\tessera-x-lab\connectivity-'+[Guid]::NewGuid().ToString('N'))
$taskDependencies=Join-Path $taskBuild 'dependencies'
$taskClasses=Join-Path $taskBuild 'classes'
New-Item -ItemType Directory -Path $taskDependencies,$taskClasses -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskBundler=(Resolve-Path -LiteralPath $BundlerPath).Path
$taskZip=[IO.Compression.ZipFile]::OpenRead($taskBundler)
try {
    foreach($taskEntry in $taskZip.Entries) {
        if($taskEntry.FullName.StartsWith('META-INF/') -and $taskEntry.FullName.EndsWith('.jar')) {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($taskEntry,(Join-Path $taskDependencies $taskEntry.Name),$true)
        }
    }
} finally {$taskZip.Dispose()}
$taskClasspath=(Get-ChildItem -LiteralPath $taskDependencies -Filter '*.jar' | ForEach-Object {$_.FullName.Replace('\','/')}) -join ';'
$taskFixture=Join-Path $taskRoot 'experiments\tessera-x\region-split\RegionConnectivityComparison.java'
$taskArguments=Join-Path $taskBuild 'javac.args'
@('--release','25','-encoding','UTF-8','-classpath',('"'+$taskClasspath+'"'),'-d',('"'+$taskClasses.Replace('\','/')+'"'),
    ('"'+$taskFixture.Replace('\','/')+'"')) | Set-Content -LiteralPath $taskArguments -Encoding utf8
& (Join-Path $JavaHome 'bin\javac.exe') ('@'+$taskArguments)
if($LASTEXITCODE -ne 0){throw 'Connectivity comparison compilation failed'}
$taskResult=Join-Path $taskBuild 'result.json'
$taskRuntime=$taskClasses.Replace('\','/')+';'+$taskClasspath
& (Join-Path $JavaHome 'bin\java.exe') '-Xms256M' '-Xmx1G' '-cp' $taskRuntime RegionConnectivityComparison $taskResult *> (Join-Path $taskBuild 'run.log')
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath (Join-Path $taskBuild 'run.log') -Tail 12;throw 'Connectivity comparison failed'}
[ordered]@{bundler=$taskBundler;bundlerSha256=(Get-FileHash -LiteralPath $taskBundler).Hash;
    fixtureSha256=(Get-FileHash -LiteralPath $taskFixture).Hash;productionServerContacted=$false} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskBuild 'invocation.json') -Encoding utf8
Get-Content -LiteralPath $taskResult
