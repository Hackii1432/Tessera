$ErrorActionPreference = 'Stop'
$taskRepository = Split-Path -Parent $PSScriptRoot
$taskBranch = 'ver/tessera-Xperimental'
$taskRef = 'refs/heads/' + $taskBranch
$taskAllowedUrl = 'https://github.com/Hackii1432/Tessera.git'
function Invoke-TaskGit {
    param([string[]]$GitArguments)
    $taskOutput = & git -C $taskRepository @GitArguments
    if ($LASTEXITCODE -ne 0) { throw ('Git failed: ' + ($GitArguments -join ' ')) }
    return $taskOutput
}
$taskCurrentBranch = (Invoke-TaskGit @('branch','--show-current')).Trim()
if ($taskCurrentBranch -ne $taskBranch) { throw 'Push refused: checkout is not ver/tessera-Xperimental.' }
$taskPushUrls = @(Invoke-TaskGit @('remote','get-url','--push','--all','origin'))
if ($taskPushUrls.Count -ne 1 -or $taskPushUrls[0] -ne $taskAllowedUrl) { throw 'Push refused: unexpected destination.' }
$taskChanges = @(Invoke-TaskGit @('status','--porcelain'))
if ($taskChanges.Count) { throw 'Push refused: commit/review the current changes first.' }
Invoke-TaskGit @('fetch','origin',($taskRef + ':refs/remotes/origin/' + $taskBranch)) | Out-Null
& git -C $taskRepository merge-base --is-ancestor ('refs/remotes/origin/' + $taskBranch) HEAD
if ($LASTEXITCODE -ne 0) { throw 'Push refused: integrate the existing branch commits first; force-push is disabled.' }
Invoke-TaskGit @('push','--porcelain','origin',($taskRef + ':' + $taskRef))
