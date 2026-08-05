param(
    [string]$JarPath,
    [ValidateRange(181, 1024)]
    [int]$MinimumPathLength = 190,
    [ValidateRange(30, 600)]
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

if ($env:OS -ne "Windows_NT") {
    throw "This smoke test is intentionally Windows-only."
}

$smokeRoot = [System.IO.Path]::GetFullPath($PSScriptRoot)
$repository = [System.IO.Path]::GetFullPath((Join-Path $smokeRoot "..\.."))
$runBase = [System.IO.Path]::GetFullPath((Join-Path $smokeRoot "build\runs"))
$runName = "run-{0}-{1}" -f (Get-Date -Format "yyyyMMdd-HHmmss-fff"), $PID
$runRoot = [System.IO.Path]::GetFullPath((Join-Path $runBase $runName))
if (-not $runRoot.StartsWith($runBase, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe smoke run directory: $runRoot"
}

if ([string]::IsNullOrWhiteSpace($JarPath)) {
    $JarPath = Get-ChildItem -LiteralPath (Join-Path $repository "folia-server\build\libs") `
        -Filter "tessera-server-*.jar" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if ([string]::IsNullOrWhiteSpace($JarPath) -or -not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
    throw "Paperclip JAR not found. Build it first or pass -JarPath."
}
$JarPath = [System.IO.Path]::GetFullPath($JarPath)

New-Item -ItemType Directory -Path $runRoot -Force | Out-Null

function New-LongDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$Base,
        [Parameter(Mandatory = $true)][int]$MinimumLength
    )

    $current = [System.IO.Path]::GetFullPath($Base)
    $index = 0
    while ($current.Length -lt $MinimumLength) {
        $segment = "segment-{0:D2}-{1}" -f $index, ("x" * 32)
        $current = Join-Path $current $segment
        $index++
    }
    New-Item -ItemType Directory -Path $current -Force | Out-Null
    return [System.IO.Path]::GetFullPath($current)
}

function Format-Argument {
    param([Parameter(Mandatory = $true)][string]$Value)
    return '"' + $Value.Replace('"', '\"') + '"'
}

function Invoke-PaperclipPatchOnly {
    param(
        [Parameter(Mandatory = $true)][string]$Label,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [AllowNull()][string]$RepositoryDirectory
    )

    $arguments = [System.Collections.Generic.List[string]]::new()
    $arguments.Add("-Dpaperclip.patchonly=true")
    if (-not [string]::IsNullOrWhiteSpace($RepositoryDirectory)) {
        $arguments.Add("-DbundlerRepoDir=$RepositoryDirectory")
    }
    $arguments.Add("-jar")
    $arguments.Add($JarPath)

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = (Get-Command java -ErrorAction Stop).Source
    $startInfo.WorkingDirectory = $WorkingDirectory
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    if ($startInfo.PSObject.Properties.Name -contains "ArgumentList") {
        foreach ($argument in $arguments) {
            $startInfo.ArgumentList.Add($argument)
        }
    } else {
        $startInfo.Arguments = ($arguments | ForEach-Object { Format-Argument $_ }) -join " "
    }

    $effectiveRepository = if ([string]::IsNullOrWhiteSpace($RepositoryDirectory)) {
        $WorkingDirectory
    } else {
        $RepositoryDirectory
    }
    $expectedOriginal = Join-Path $effectiveRepository "cache\mojang_26.2.jar"
    Write-Host "[$Label] workingDirectory=$WorkingDirectory (length=$($WorkingDirectory.Length))"
    Write-Host "[$Label] repositoryDirectory=$effectiveRepository (length=$($effectiveRepository.Length))"
    Write-Host "[$Label] expectedOriginal=$expectedOriginal (length=$($expectedOriginal.Length))"

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    if (-not $process.Start()) {
        throw "[$Label] Java process did not start."
    }
    $stdoutTask = $process.StandardOutput.ReadToEndAsync()
    $stderrTask = $process.StandardError.ReadToEndAsync()
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while (-not $process.WaitForExit(5000)) {
        $remaining = [Math]::Max(0, [int]($deadline - [DateTime]::UtcNow).TotalSeconds)
        Write-Host "[$Label] Paperclip is still running; ${remaining}s remain."
        if ([DateTime]::UtcNow -ge $deadline) {
            try {
                $process.Kill()
            } catch {
                Write-Warning "[$Label] Could not terminate timed-out process: $_"
            }
            $process.WaitForExit()
            throw "[$Label] Paperclip timed out after $TimeoutSeconds seconds."
        }
    }

    $stdout = $stdoutTask.GetAwaiter().GetResult()
    $stderr = $stderrTask.GetAwaiter().GetResult()
    $combined = $stdout + [Environment]::NewLine + $stderr
    if ($process.ExitCode -ne 0) {
        Write-Host $combined
        $accessDenied = $combined -match "AccessDeniedException"
        $failure = "[$Label] Paperclip bootstrap failed before Tessera started. " +
            "exit=$($process.ExitCode), accessDenied=$accessDenied, " +
            "workingLength=$($WorkingDirectory.Length), repositoryLength=$($effectiveRepository.Length), " +
            "expectedOriginalLength=$($expectedOriginal.Length)"
        throw $failure
    }
    if ($combined -match "AccessDeniedException") {
        throw "[$Label] Paperclip reported AccessDeniedException despite exit code 0."
    }

    $original = Get-ChildItem -LiteralPath $effectiveRepository -Recurse -File -Filter "mojang_*.jar" |
        Select-Object -First 1 -ExpandProperty FullName
    if ([string]::IsNullOrWhiteSpace($original)) {
        throw "[$Label] Paperclip succeeded but no cached Mojang JAR was found under $effectiveRepository."
    }
    return [pscustomobject]@{
        Label = $Label
        RepositoryDirectory = $effectiveRepository
        OriginalJar = [System.IO.Path]::GetFullPath($original)
        Output = $combined
    }
}

function Copy-OriginalJar {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$TargetRepository
    )

    $target = Join-Path $TargetRepository "cache\$([System.IO.Path]::GetFileName($Source))"
    New-Item -ItemType Directory -Path ([System.IO.Path]::GetDirectoryName($target)) -Force | Out-Null
    Copy-Item -LiteralPath $Source -Destination $target -Force
}

$longPathsEnabled = try {
    (Get-ItemProperty `
        -LiteralPath "HKLM:\SYSTEM\CurrentControlSet\Control\FileSystem" `
        -Name "LongPathsEnabled" `
        -ErrorAction Stop).LongPathsEnabled
} catch {
    "unavailable"
}

Write-Host "Tessera Windows Paperclip long-path smoke test"
Write-Host "jar=$JarPath"
Write-Host "jarSha256=$((Get-FileHash -LiteralPath $JarPath -Algorithm SHA256).Hash)"
$javaVersion = (& cmd.exe /d /c "java -version 2>&1") -join " | "
Write-Host "java=$javaVersion"
Write-Host "os=$([Environment]::OSVersion.VersionString)"
Write-Host "LongPathsEnabled=$longPathsEnabled"

$shortWorking = Join-Path $runRoot "short-control"
New-Item -ItemType Directory -Path $shortWorking -Force | Out-Null
$control = Invoke-PaperclipPatchOnly `
    -Label "short-control" `
    -WorkingDirectory $shortWorking `
    -RepositoryDirectory $null

$longWorking = New-LongDirectory `
    -Base (Join-Path $runRoot "long-working-directory") `
    -MinimumLength $MinimumPathLength
Copy-OriginalJar -Source $control.OriginalJar -TargetRepository $longWorking
$longWorkingResult = Invoke-PaperclipPatchOnly `
    -Label "long-working-directory" `
    -WorkingDirectory $longWorking `
    -RepositoryDirectory $null

$explicitWorking = Join-Path $runRoot "explicit-repository-control"
New-Item -ItemType Directory -Path $explicitWorking -Force | Out-Null
$longRepository = New-LongDirectory `
    -Base (Join-Path $runRoot "long-explicit-repository") `
    -MinimumLength $MinimumPathLength
Copy-OriginalJar -Source $control.OriginalJar -TargetRepository $longRepository
$longRepositoryResult = Invoke-PaperclipPatchOnly `
    -Label "long-explicit-repository" `
    -WorkingDirectory $explicitWorking `
    -RepositoryDirectory $longRepository

foreach ($result in @($longWorkingResult, $longRepositoryResult)) {
    if ($result.OriginalJar.Length -le 180) {
        throw "[$($result.Label)] Cached Mojang path was not long enough: $($result.OriginalJar)"
    }
    $probe = Join-Path ([System.IO.Path]::GetDirectoryName($result.OriginalJar)) "probe.jar"
    Move-Item -LiteralPath $result.OriginalJar -Destination $probe
    Move-Item -LiteralPath $probe -Destination $result.OriginalJar
}

Write-Host "PASS: Paperclip first extraction and ZipFS close succeeded in all Windows path cases."
Write-Host "Artifacts retained at $runRoot because Windows PowerShell 5.1 cannot reliably remove the generated long paths."
