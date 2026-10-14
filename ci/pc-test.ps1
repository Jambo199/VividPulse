# Tests Vivid Pulse PC (the Windows program) on a Windows runner: a pretend Steam install (with a
# download going), its --once output, then the program itself answering (code check, finding it on
# the network, every endpoint). The outputs go into pc-out for the emulator test and for reading.
# Problems are collected and reported at the end, so one doesn't hide the rest.
param([string]$Exe = "pc\VividPulsePC\bin\Release\net472\VividPulsePC.exe")
$ErrorActionPreference = "Continue"
$repo = Split-Path $PSScriptRoot
$Exe = (Resolve-Path $Exe).Path
$out = Join-Path $repo "pc-out"
New-Item -ItemType Directory -Force $out | Out-Null

Write-Host "== A pretend Steam install"
$steam = Join-Path $env:RUNNER_TEMP "Steam"
$lib2 = Join-Path $env:RUNNER_TEMP "SteamLibrary"
$accountId = 39734273   # SteamID 76561198000000001
$userConfig = Join-Path $steam "userdata\$accountId\config"
New-Item -ItemType Directory -Force "$steam\steamapps", "$steam\config", "$lib2\steamapps", "$userConfig\cloudstorage" | Out-Null
foreach ($id in 1091500, 271590, 570, 1808500) { New-Item -ItemType Directory -Force "$steam\appcache\librarycache\$id" | Out-Null }
Set-Content "$steam\appcache\librarycache\730_library_600x900.jpg" "x"
function Write-Text($path, $text) { [IO.File]::WriteAllText($path, $text) }
Write-Text "$steam\steamapps\libraryfolders.vdf" (@"
"libraryfolders"
{
	"0"
	{
		"path"		"$($steam -replace '\\', '\\')"
	}
	"1"
	{
		"path"		"$($lib2 -replace '\\', '\\')"
	}
}
"@)
function Write-Manifest($dir, $id, $name, $size) {
    Write-Text "$dir\steamapps\appmanifest_$id.acf" (@"
"AppState"
{
	"appid"		"$id"
	"name"		"$name"
	"SizeOnDisk"		"$size"
	"LastUpdated"		"1759000000"
}
"@)
}
Write-Manifest $steam 1091500 "Cyberpunk 2077" 74512345678
Write-Manifest $steam 228980 "Steamworks Common Redistributables" 123456
Write-Manifest $lib2 271590 "Grand Theft Auto V Legacy" 105000000000
# Downloads: ARC Raiders downloading (StateFlags 1282 = update required, started, running), Counter-Strike 2's update queued.
function Write-Download($id, $name, $flags, $total, $done) {
    Write-Text "$steam\steamapps\appmanifest_$id.acf" (@"
"AppState"
{
	"appid"		"$id"
	"name"		"$name"
	"StateFlags"		"$flags"
	"SizeOnDisk"		"1000"
	"LastUpdated"		"1759000000"
	"BytesToDownload"		"$total"
	"BytesDownloaded"		"$done"
}
"@)
}
Write-Download 1808500 "ARC Raiders" 1282 36000000000 9000000000
Write-Download 730 "Counter-Strike 2" 6 1200000000 0
Write-Text "$steam\config\loginusers.vdf" (@"
"users"
{
	"76561198000000001"
	{
		"AccountName"		"citester"
		"PersonaName"		"CI Tester"
		"MostRecent"		"1"
		"Timestamp"		"1760000000"
	}
}
"@)
Write-Text "$userConfig\localconfig.vdf" (@"
"UserLocalConfigStore"
{
	"Software"
	{
		"Valve"
		{
			"Steam"
			{
				"apps"
				{
					"1091500"
					{
						"LastPlayed"		"1760300000"
						"Playtime"		"6120"
						"cloud"
						{
							"last_sync_state"		"synchronized"
						}
					}
					"570"
					{
						"Playtime"		"120"
					}
				}
			}
		}
	}
}
"@)
# Steam's own format, written out by hand: each collection is a JSON string inside the JSON.
function Entry($key, $value) {
    $head = '["user-collections.' + $key + '",{"key":"user-collections.' + $key + '","timestamp":1700000000,"version":"1",'
    if ($null -eq $value) { return $head + '"is_deleted":true}]' }
    return $head + '"value":"' + $value.Replace('\', '\\').Replace('"', '\"') + '"}]'
}
$entries = @(
    (Entry "favorite" '{"id":"favorite","name":"Favorites","added":[1091500],"removed":[]}'),
    (Entry "uc-AAAA" '{"id":"uc-AAAA","name":"Purchased","added":[1091500,271590,570,1808500],"removed":[570]}'),
    (Entry "uc-BBBB" '{"id":"uc-BBBB","name":"Installed","added":[],"removed":[],"filterSpec":{"nFormatVersion":2,"rgOptions":[]}}'),
    (Entry "uc-CCCC" $null)
)
Write-Text "$userConfig\cloudstorage\cloud-storage-namespace-1.json" ("[" + ($entries -join ",") + "]")
Get-Content "$userConfig\cloudstorage\cloud-storage-namespace-1.json" | Write-Host
New-Item -Path "HKCU:\Software\Valve\Steam" -Force | Out-Null
Set-ItemProperty "HKCU:\Software\Valve\Steam" -Name SteamPath -Value ($steam -replace '\\', '/')
Set-ItemProperty "HKCU:\Software\Valve\Steam" -Name RunningAppID -Value 1091500 -Type DWord

Write-Host "== --once"
$sw = [Diagnostics.Stopwatch]::StartNew()
$once = Start-Process $Exe -ArgumentList "--once `"$out`"" -Wait -PassThru
Write-Host "took $([math]::Round($sw.Elapsed.TotalSeconds, 1)) s, exit code $($once.ExitCode)"
if (Test-Path "$out\error.txt") { Get-Content "$out\error.txt" | Write-Host; throw "--once failed" }
Get-ChildItem $out | Format-Table Name, Length | Out-String | Write-Host

Write-Host "== The server"
$p = Start-Process $Exe -ArgumentList "--headless" -PassThru
$up = $false
for ($i = 0; $i -lt 90; $i++) {
    try { $c = New-Object Net.Sockets.TcpClient; $c.Connect("127.0.0.1", 47800); $c.Close(); $up = $true; break } catch { Start-Sleep -Seconds 1 }
}
if (-not $up) { Get-Content (Join-Path $env:APPDATA "VividPulsePC\log.txt") -ErrorAction SilentlyContinue | Write-Host; throw "The server didn't start" }
Write-Host "listening after about $i s"
$code = (Get-Content (Join-Path $env:APPDATA "VividPulsePC\config.json") -Raw | ConvertFrom-Json).code
Write-Host "code: $code"
$failures = @()
function Ask($path, $header, $method = "GET") {
    $curlArgs = @("-s", "-m", "30", "-X", $method, "-o", "$out\answer.tmp", "-w", "%{http_code}")
    if ($header) { $curlArgs += @("-H", $header) }
    $status = & curl.exe @curlArgs "http://127.0.0.1:47800$path"
    $body = if (Test-Path "$out\answer.tmp") { Get-Content "$out\answer.tmp" -Raw } else { "" }
    Remove-Item "$out\answer.tmp" -ErrorAction SilentlyContinue
    return @{ status = [int]$status; body = $body }
}
function Expect($label, $answer, $status) {
    $preview = if ($answer.body) { $answer.body.Substring(0, [math]::Min(160, $answer.body.Length)) } else { "" }
    if ($answer.status -eq $status) { Write-Host "ok   $label -> $($answer.status) $preview" }
    else { Write-Host "FAIL $label -> $($answer.status) (wanted $status) $preview"; $script:failures += $label }
}
Expect "/hello without a code" (Ask "/hello") 200
Expect "/specs without a code" (Ask "/specs") 401
Expect "/specs with a wrong code" (Ask "/specs" "X-Pulse-Code: 1") 401
$auth = "X-Pulse-Code: $code"
$specs = Ask "/specs" $auth; Expect "/specs" $specs 200
$sw = [Diagnostics.Stopwatch]::StartNew()
$live = Ask "/live" $auth; Expect "/live" $live 200
Write-Host "     /live took $($sw.ElapsedMilliseconds) ms"
$steamAnswer = Ask "/steam?refresh=1" $auth; Expect "/steam?refresh=1" $steamAnswer 200
$downloads = Ask "/downloads" $auth; Expect "/downloads" $downloads 200
$sw.Restart(); $null = Ask "/live" $auth; Write-Host "     /live again took $($sw.ElapsedMilliseconds) ms (readings are kept ready)"
$launch = Ask "/launch?appid=1091500" $auth "POST"
Write-Host "     /launch -> $($launch.status) $($launch.body) (200, or 500 when nothing on the runner handles steam:// links)"
if ($launch.status -ne 200 -and $launch.status -ne 500) { $failures += "/launch" }
Expect "/nothing" (Ask "/nothing" $auth) 404
[IO.File]::WriteAllText("$out\server-specs.json", $specs.body)
[IO.File]::WriteAllText("$out\server-live.json", $live.body)
[IO.File]::WriteAllText("$out\server-steam.json", $steamAnswer.body)
[IO.File]::WriteAllText("$out\server-downloads.json", $downloads.body)

Write-Host "== Finding it on the network"
$udp = New-Object Net.Sockets.UdpClient
$udp.Client.ReceiveTimeout = 5000
$hello = [Text.Encoding]::ASCII.GetBytes("VIVIDPULSE_DISCOVER")
[void]$udp.Send($hello, $hello.Length, "127.0.0.1", 47801)
try {
    $from = New-Object Net.IPEndPoint([Net.IPAddress]::Any, 0)
    $reply = [Text.Encoding]::UTF8.GetString($udp.Receive([ref]$from))
    Write-Host "ok   discovery reply from $($from): $reply"
    if (-not $reply.Contains("VividPulsePC")) { $failures += "discovery reply" }
} catch { Write-Host "FAIL no discovery reply: $($_.Exception.Message)"; $failures += "discovery" }

Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 1
Write-Host "== Its log"
Get-Content (Join-Path $env:APPDATA "VividPulsePC\log.txt") -ErrorAction SilentlyContinue | Write-Host
if ($failures.Count -gt 0) { throw "Failed: $($failures -join ', ')" }
Write-Host "All server checks passed"
exit 0
