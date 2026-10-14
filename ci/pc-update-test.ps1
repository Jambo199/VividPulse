# Vivid Pulse PC updating itself: a copy of the program finds a newer build on a pretend release
# page (served on this computer), downloads it, checks it, swaps itself and starts again. Also: it
# leaves itself alone when it's the latest, and when the download doesn't match its checksum.
$ErrorActionPreference = "Continue"
$repo = Split-Path $PSScriptRoot
$site = Join-Path $env:RUNNER_TEMP "pretend-release"
$app = Join-Path $env:RUNNER_TEMP "Vivid Pulse PC copy"
New-Item -ItemType Directory -Force $site, $app | Out-Null
Copy-Item (Join-Path $repo "VividPulsePC.zip") $site
Copy-Item "$repo\pc\VividPulsePC\bin\Release\net472\VividPulsePC.exe", "$repo\pc\VividPulsePC\bin\Release\net472\VividPulsePC.exe.config" $app
$published = Get-Content (Join-Path $repo "pc-version.json") -Raw | ConvertFrom-Json
Write-Host "published: $($published | ConvertTo-Json -Compress)"
$log = Join-Path $env:APPDATA "VividPulsePC\log.txt"
$failures = @()

$server = Start-Process python -ArgumentList "-m", "http.server", "8765", "--bind", "127.0.0.1", "--directory", "`"$site`"" -PassThru -WindowStyle Hidden
Start-Sleep -Seconds 3
$env:VIVIDPULSE_UPDATE_URL = "http://127.0.0.1:8765/"

function Publish($build, $sha) {
    $o = [ordered]@{ build = $build; version = "2.$build"; zip = "VividPulsePC.zip"; sha256 = $sha; notes = "A test build" }
    [IO.File]::WriteAllText("$site\pc-version.json", ($o | ConvertTo-Json))
}
function Update() {
    $p = Start-Process "$app\VividPulsePC.exe" -ArgumentList "--update --headless" -Wait -PassThru
    return $p.ExitCode
}
$original = (Get-FileHash "$app\VividPulsePC.exe").Hash

Write-Host "== Already the latest"
Publish $published.build $published.sha256
$code = Update
if ($code -eq 2) { Write-Host "ok   nothing to do (exit 2)" } else { Write-Host "FAIL exit $code (wanted 2)"; $failures += "latest" }

Write-Host "== A download that doesn't match its checksum"
Publish ($published.build + 1) ("0" * 64)
$code = Update
$left = Get-ChildItem $app | Select-Object -ExpandProperty Name
if ($code -eq 1 -and (Get-FileHash "$app\VividPulsePC.exe").Hash -eq $original -and $left.Count -eq 2) { Write-Host "ok   refused, program left as it was ($($left -join ', '))" }
else { Write-Host "FAIL exit $code, files: $($left -join ', ')"; $failures += "checksum" }

Write-Host "== A newer build"
Publish ($published.build + 1) $published.sha256
$code = Update
if ($code -eq 0) { Write-Host "ok   updated (exit 0)" } else { Write-Host "FAIL exit $code (wanted 0)"; $failures += "update" }
# The new copy starts by itself, waits for the old one to close, answers and tidies up.
$hello = $null
for ($i = 0; $i -lt 30 -and -not $hello; $i++) {
    Start-Sleep -Seconds 1
    try { $hello = Invoke-RestMethod "http://127.0.0.1:47800/hello" -TimeoutSec 3 } catch { }
}
if ($hello) { Write-Host "ok   the new copy answers: $($hello | ConvertTo-Json -Compress)" } else { Write-Host "FAIL the new copy didn't start"; $failures += "restart" }
for ($i = 0; $i -lt 20 -and (Test-Path "$app\VividPulsePC.old.exe"); $i++) { Start-Sleep -Seconds 1 }
$left = Get-ChildItem $app | Select-Object -ExpandProperty Name
if (-not (Test-Path "$app\VividPulsePC.old.exe") -and -not (Test-Path "$app\VividPulsePC.exe.new")) { Write-Host "ok   the old copy is gone: $($left -join ', ')" }
else { Write-Host "FAIL left behind: $($left -join ', ')"; $failures += "cleanup" }
if ((Get-Content $log -Raw) -match "started after updating") { Write-Host "ok   its log says it started after updating" } else { $failures += "log" }

Get-Process VividPulsePC -ErrorAction SilentlyContinue | Stop-Process -Force
Stop-Process -Id $server.Id -Force -ErrorAction SilentlyContinue
Write-Host "== Its log"
Get-Content $log -Tail 15 | Write-Host
if ($failures.Count -gt 0) { throw "Failed: $($failures -join ', ')" }
Write-Host "All update checks passed"
exit 0
