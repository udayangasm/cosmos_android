# Auto commit & push for COSMOS_ANDROID
# Runs at logon (via Task Scheduler). Commits any local changes and pushes to origin/main.
# Log: %LOCALAPPDATA%\cosmos-autopush.log

$ErrorActionPreference = 'Continue'
$repo   = 'C:\COSMOS_ANDROID'
$branch = 'main'
$log    = Join-Path $env:LOCALAPPDATA 'cosmos-autopush.log'

function Log($m) {
    $ts = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    "$ts  $m" | Out-File -FilePath $log -Append -Encoding utf8
}

Log '--- run start ---'

if (-not (Test-Path (Join-Path $repo '.git'))) {
    Log "ERROR: no git repo at $repo"
    return
}
Set-Location $repo

# Stage everything (respects .gitignore)
git add -A

# Commit only if there is something staged
git diff --cached --quiet
if ($LASTEXITCODE -ne 0) {
    $msg = "Auto-commit: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    git commit -m $msg | Out-Null
    Log "committed -> $msg"
} else {
    Log 'no local changes to commit'
}

# Push with retries (handles network not being ready right after logon)
$pushed = $false
for ($i = 1; $i -le 12; $i++) {
    $out = git push origin $branch 2>&1
    foreach ($line in $out) { Log "push: $line" }
    if ($LASTEXITCODE -eq 0) { $pushed = $true; break }
    Log "push attempt $i failed (exit $LASTEXITCODE); retry in 10s"
    Start-Sleep -Seconds 10
}
Log "push success: $pushed"
Log '--- run end ---'
