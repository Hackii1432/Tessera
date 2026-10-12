param([Parameter(Mandatory)][string]$BundlerPath,[string]$JavaHome=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
if(-not $JavaHome){throw 'Set -JavaHome to JDK 25'}
$taskRoot=(Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$taskRun=Join-Path $taskRoot ('build\tessera-x-lab\config-check-'+[Guid]::NewGuid().ToString('N'))
$taskDeps=Join-Path $taskRun 'dependencies';$taskClasses=Join-Path $taskRun 'classes'
New-Item -ItemType Directory -Path $taskDeps,$taskClasses -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskBundler=(Resolve-Path -LiteralPath $BundlerPath).Path
$taskZip=[IO.Compression.ZipFile]::OpenRead($taskBundler)
try{foreach($taskEntry in $taskZip.Entries){if($taskEntry.FullName.StartsWith('META-INF/') -and $taskEntry.FullName.EndsWith('.jar')){
    [IO.Compression.ZipFileExtensions]::ExtractToFile($taskEntry,(Join-Path $taskDeps $taskEntry.Name),$true)
}}}finally{$taskZip.Dispose()}
$taskClasspath=(Get-ChildItem -LiteralPath $taskDeps -Filter '*.jar' | ForEach-Object {$_.FullName.Replace('\','/')}) -join ';'
$taskFixture=Join-Path $taskRoot 'experiments\tessera-x\config\ExperimentalOptionsCheck.java'
$taskArgs=Join-Path $taskRun 'javac.args'
@('--release','25','-encoding','UTF-8','-classpath',('"'+$taskClasspath+'"'),'-d',('"'+$taskClasses.Replace('\','/')+'"'),('"'+$taskFixture.Replace('\','/')+'"')) |
    Set-Content -LiteralPath $taskArgs -Encoding utf8
& (Join-Path $JavaHome 'bin\javac.exe') ('@'+$taskArgs)
if($LASTEXITCODE -ne 0){throw 'Config fixture compilation failed'}
$taskRuntime=$taskClasses.Replace('\','/')+';'+$taskClasspath
& (Join-Path $JavaHome 'bin\java.exe') '--enable-native-access=ALL-UNNAMED' '-cp' $taskRuntime dev.tessera.x.ExperimentalOptionsCheck $taskRun *> (Join-Path $taskRun 'run.log')
if($LASTEXITCODE -ne 0){Get-Content -LiteralPath (Join-Path $taskRun 'run.log') -Tail 15;throw 'Config checks failed'}
Get-Content -LiteralPath (Join-Path $taskRun 'result.json')
Write-Host "Config check PASS: $taskRun"
