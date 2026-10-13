<#
  Vivid Pulse PC: lets the Vivid Pulse app on your phone see this PC's specs, live usage and
  Steam library over your home Wi-Fi.

  It only answers on your local network, only to a phone that knows the 6-digit code shown below,
  and only reads things (the one thing it can do is start a Steam game you pick on the phone).

  Start it with "Start Vivid Pulse PC.cmd". Options:
    -InstallStartup   start it (minimised) whenever you sign in to Windows
    -RemoveStartup    stop starting it with Windows
    -AllowFirewall    add Windows Firewall rules for private networks (asks for admin)
    -Once <path>      testing: write hello/specs/live/steam JSON into <path> and exit
#>
param(
    [switch]$InstallStartup,
    [switch]$RemoveStartup,
    [switch]$AllowFirewall,
    [string]$Once = ""
)

$ErrorActionPreference = "Continue"
$Version = "1.0"
$Port = 47800
$DiscoveryPort = 47801
$ConfigDir = Join-Path $env:APPDATA "VividPulsePC"
$ConfigFile = Join-Path $ConfigDir "config.json"
$StartupLink = Join-Path ([Environment]::GetFolderPath("Startup")) "Vivid Pulse PC.lnk"

# ---------------------------------------------------------------- setup options

if ($InstallStartup) {
    $shell = New-Object -ComObject WScript.Shell
    $link = $shell.CreateShortcut($StartupLink)
    $link.TargetPath = "powershell.exe"
    $link.Arguments = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Minimized -File `"$PSCommandPath`""
    $link.WorkingDirectory = Split-Path $PSCommandPath
    $link.WindowStyle = 7
    $link.Save()
    Write-Host "Vivid Pulse PC will start (minimised) when you sign in to Windows."
    exit 0
}
if ($RemoveStartup) {
    Remove-Item $StartupLink -ErrorAction SilentlyContinue
    Write-Host "Vivid Pulse PC won't start with Windows any more."
    exit 0
}
if ($AllowFirewall) {
    $admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $admin) {
        Start-Process powershell.exe -Verb RunAs -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -AllowFirewall"
        exit 0
    }
    Remove-NetFirewallRule -DisplayName "Vivid Pulse PC*" -ErrorAction SilentlyContinue
    New-NetFirewallRule -DisplayName "Vivid Pulse PC (app)" -Direction Inbound -Protocol TCP -LocalPort $Port -Profile Private -Action Allow | Out-Null
    New-NetFirewallRule -DisplayName "Vivid Pulse PC (finding it)" -Direction Inbound -Protocol UDP -LocalPort $DiscoveryPort -Profile Private -Action Allow | Out-Null
    Write-Host "Done: your phone can reach Vivid Pulse PC on private (home) networks."
    Start-Sleep -Seconds 3
    exit 0
}

# ---------------------------------------------------------------- the pairing code

function Get-Config {
    if (Test-Path $ConfigFile) {
        try { return Get-Content $ConfigFile -Raw | ConvertFrom-Json } catch { }
    }
    New-Item -ItemType Directory -Force -Path $ConfigDir | Out-Null
    $config = [pscustomobject]@{ code = "{0:D6}" -f (Get-Random -Minimum 0 -Maximum 1000000) }
    $config | ConvertTo-Json | Set-Content $ConfigFile -Encoding UTF8
    return $config
}

# ---------------------------------------------------------------- reading the PC

function Get-Number($value) { if ($null -eq $value) { return $null } else { return [double]$value } }

# Graphics memory: Windows' own count is capped at 4 GB, so read the driver's figure from the registry.
function Get-VramByName {
    $result = @{}
    $class = "HKLM:\SYSTEM\CurrentControlSet\Control\Class\{4d36e968-e325-11ce-bfc1-08002be10318}"
    Get-ChildItem $class -ErrorAction SilentlyContinue | ForEach-Object {
        $p = Get-ItemProperty $_.PSPath -ErrorAction SilentlyContinue
        if ($p -and $p.DriverDesc) {
            $bytes = $p.'HardwareInformation.qwMemorySize'
            if (-not $bytes) { $bytes = $p.'HardwareInformation.MemorySize' }
            if ($bytes -is [byte[]]) { $bytes = [BitConverter]::ToUInt32($bytes, 0) }
            if ($bytes) { $result[$p.DriverDesc] = [double]$bytes }
        }
    }
    return $result
}

function Get-Nvidia {
    $smi = Get-Command nvidia-smi -ErrorAction SilentlyContinue
    if (-not $smi) {
        $path = Join-Path $env:SystemRoot "System32\nvidia-smi.exe"
        if (Test-Path $path) { $smi = $path } else { return $null }
    } else { $smi = $smi.Source }
    try {
        $line = & $smi --query-gpu=name,temperature.gpu,utilization.gpu,memory.used,memory.total,power.draw,clocks.gr,fan.speed --format=csv,noheader,nounits 2>$null | Select-Object -First 1
        if (-not $line) { return $null }
        $f = $line.Split(",") | ForEach-Object { $_.Trim() }
        function n($s) { $d = 0.0; if ([double]::TryParse($s, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$d)) { return $d } else { return $null } }
        return [ordered]@{
            name = $f[0]; tempC = n $f[1]; loadPercent = n $f[2]
            vramUsedMb = n $f[3]; vramTotalMb = n $f[4]; powerW = n $f[5]; clockMhz = n $f[6]; fanPercent = n $f[7]
        }
    } catch { return $null }
}

function Get-Specs {
    $cs = Get-CimInstance Win32_ComputerSystem
    $os = Get-CimInstance Win32_OperatingSystem
    $cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
    $nt = Get-ItemProperty "HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion" -ErrorAction SilentlyContinue
    $vram = Get-VramByName
    $gpus = @(Get-CimInstance Win32_VideoController | Where-Object { $_.Name } | ForEach-Object {
        $v = $vram[$_.Name]
        if (-not $v -and $_.AdapterRAM) { $v = [double]$_.AdapterRAM }
        [ordered]@{
            name = $_.Name
            vramBytes = $v
            driver = $_.DriverVersion
            width = Get-Number $_.CurrentHorizontalResolution
            height = Get-Number $_.CurrentVerticalResolution
            refreshHz = Get-Number $_.CurrentRefreshRate
        }
    })
    $memoryType = @{ 20 = "DDR"; 21 = "DDR2"; 24 = "DDR3"; 26 = "DDR4"; 30 = "LPDDR4"; 34 = "DDR5"; 35 = "LPDDR5" }
    $sticks = @(Get-CimInstance Win32_PhysicalMemory | ForEach-Object {
        [ordered]@{
            bytes = [double]$_.Capacity
            speedMts = Get-Number $_.Speed
            configuredMts = Get-Number $_.ConfiguredClockSpeed
            type = $memoryType[[int]$_.SMBIOSMemoryType]
            maker = ("" + $_.Manufacturer).Trim()
            part = ("" + $_.PartNumber).Trim()
            slot = $_.DeviceLocator
        }
    })
    $physical = @{}
    try { Get-PhysicalDisk -ErrorAction Stop | ForEach-Object { $physical[$_.FriendlyName] = $_ } } catch { }
    $drives = @(Get-CimInstance Win32_DiskDrive | ForEach-Object {
        $p = $physical[$_.Model]
        [ordered]@{
            model = $_.Model
            bytes = [double]$_.Size
            kind = if ($p) { "" + $p.MediaType } else { $null }
            bus = if ($p) { "" + $p.BusType } else { "" + $_.InterfaceType }
        }
    })
    $volumes = @(Get-CimInstance Win32_LogicalDisk -Filter "DriveType=3" | ForEach-Object {
        [ordered]@{ letter = $_.DeviceID; label = $_.VolumeName; bytes = [double]$_.Size; freeBytes = [double]$_.FreeSpace }
    })
    $monitors = @(Get-CimInstance -Namespace root\wmi -ClassName WmiMonitorID -ErrorAction SilentlyContinue | ForEach-Object {
        $name = -join ($_.UserFriendlyName | Where-Object { $_ -ne 0 } | ForEach-Object { [char]$_ })
        $maker = -join ($_.ManufacturerName | Where-Object { $_ -ne 0 } | ForEach-Object { [char]$_ })
        [ordered]@{ name = $name; maker = $maker }
    })
    $board = Get-CimInstance Win32_BaseBoard
    $bios = Get-CimInstance Win32_BIOS
    return [ordered]@{
        name = $env:COMPUTERNAME
        maker = $cs.Manufacturer
        model = $cs.Model
        os = [ordered]@{
            name = $os.Caption
            version = $nt.DisplayVersion
            build = "$($os.BuildNumber).$($nt.UBR)"
            installed = $os.InstallDate.ToString("yyyy-MM-dd")
        }
        cpu = [ordered]@{
            name = ("" + $cpu.Name).Trim()
            cores = Get-Number $cpu.NumberOfCores
            threads = Get-Number $cpu.NumberOfLogicalProcessors
            maxMhz = Get-Number $cpu.MaxClockSpeed
            l2Kb = Get-Number $cpu.L2CacheSize
            l3Kb = Get-Number $cpu.L3CacheSize
            socket = $cpu.SocketDesignation
        }
        gpus = $gpus
        memory = [ordered]@{ totalBytes = [double]$cs.TotalPhysicalMemory; sticks = $sticks }
        board = [ordered]@{ maker = $board.Manufacturer; model = $board.Product; bios = "$($bios.Manufacturer) $($bios.SMBIOSBIOSVersion)".Trim() }
        drives = $drives
        volumes = $volumes
        monitors = $monitors
    }
}

$script:MaxMhz = $null

function Get-Live {
    $os = Get-CimInstance Win32_OperatingSystem
    $cores = @(Get-CimInstance Win32_PerfFormattedData_PerfOS_Processor -ErrorAction SilentlyContinue)
    $total = $cores | Where-Object { $_.Name -eq "_Total" } | Select-Object -First 1
    $perCore = @($cores | Where-Object { $_.Name -ne "_Total" } | Sort-Object { [int]$_.Name } | ForEach-Object { [double]$_.PercentProcessorTime })
    $info = Get-CimInstance Win32_PerfFormattedData_Counters_ProcessorInformation -Filter "Name='_Total'" -ErrorAction SilentlyContinue
    # The base clock doesn't change: read it once.
    if (-not $script:MaxMhz) { $script:MaxMhz = (Get-CimInstance Win32_Processor | Select-Object -First 1).MaxClockSpeed }
    $clock = $null
    if ($info -and $info.PercentProcessorPerformance) { $clock = [math]::Round($script:MaxMhz * $info.PercentProcessorPerformance / 100) }
    $gpu = Get-Nvidia
    if (-not $gpu) {
        # Other graphics cards: Windows' own GPU counters (no temperature).
        $engines = @(Get-CimInstance Win32_PerfFormattedData_GPUPerformanceCounters_GPUEngine -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "*engtype_3D*" })
        $memory = @(Get-CimInstance Win32_PerfFormattedData_GPUPerformanceCounters_GPUAdapterMemory -ErrorAction SilentlyContinue)
        if ($engines.Count -gt 0) {
            $load = ($engines | Measure-Object -Property UtilizationPercentage -Sum).Sum
            $used = ($memory | Measure-Object -Property DedicatedUsage -Sum).Sum
            $gpu = [ordered]@{ name = $null; tempC = $null; loadPercent = [math]::Min(100, [double]$load); vramUsedMb = [math]::Round($used / 1MB); vramTotalMb = $null; powerW = $null; clockMhz = $null; fanPercent = $null }
        }
    }
    $net = @(Get-CimInstance Win32_PerfFormattedData_Tcpip_NetworkInterface -ErrorAction SilentlyContinue)
    $steam = Get-ItemProperty "HKCU:\Software\Valve\Steam" -ErrorAction SilentlyContinue
    $running = 0
    if ($steam -and $steam.RunningAppID) { $running = [int]$steam.RunningAppID }
    return [ordered]@{
        at = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
        cpuPercent = if ($total) { [double]$total.PercentProcessorTime } else { $null }
        cpuCores = $perCore
        cpuMhz = $clock
        memoryUsedBytes = ([double]$os.TotalVisibleMemorySize - [double]$os.FreePhysicalMemory) * 1024
        memoryTotalBytes = [double]$os.TotalVisibleMemorySize * 1024
        gpu = $gpu
        downBytesPerSec = [double](($net | Measure-Object -Property BytesReceivedPersec -Sum).Sum)
        upBytesPerSec = [double](($net | Measure-Object -Property BytesSentPersec -Sum).Sum)
        uptimeSec = [math]::Round(((Get-Date) - $os.LastBootUpTime).TotalSeconds)
        playingAppId = $running
    }
}

# Steam's files are in Valve's "KeyValues" text format. Reads the keys and values under each app
# in an "apps" section (playtime and when last played).
function Read-VdfApps($path) {
    $apps = @{}
    if (-not (Test-Path $path)) { return $apps }
    $text = [IO.File]::ReadAllText($path)
    $tokens = [regex]::Matches($text, '"((?:[^"\\]|\\.)*)"|[{}]')
    $stack = New-Object System.Collections.Generic.List[string]
    $key = $null
    foreach ($m in $tokens) {
        $v = $m.Value
        if ($v -eq "{") { $stack.Add([string]$key); $key = $null; continue }
        if ($v -eq "}") { if ($stack.Count -gt 0) { $stack.RemoveAt($stack.Count - 1) }; $key = $null; continue }
        $s = $m.Groups[1].Value
        if ($null -eq $key) { $key = $s; continue }
        $n = $stack.Count
        if ($n -ge 2 -and $stack[$n - 2] -eq "apps" -and ($key -eq "Playtime" -or $key -eq "LastPlayed")) {
            $id = $stack[$n - 1]
            if (-not $apps.ContainsKey($id)) { $apps[$id] = @{} }
            $apps[$id][$key] = [double]$s
        }
        $key = $null
    }
    return $apps
}

# Your Steam collections (Favorites, and ones you made). Steam keeps them in a JSON file it syncs.
# A collection you fill by hand lists its games; a dynamic one (made from filters) doesn't.
function Get-Collections($root, $accountId) {
    $result = New-Object System.Collections.Generic.List[object]
    $file = Join-Path $root "userdata\$accountId\config\cloudstorage\cloud-storage-namespace-1.json"
    if (-not (Test-Path $file)) { return $result }
    try { $entries = [IO.File]::ReadAllText($file) | ConvertFrom-Json } catch { return $result }
    foreach ($e in $entries) {
        $key = "" + $e[0]
        $data = $e[1]
        if (-not $key.StartsWith("user-collections.") -or -not $data -or $data.is_deleted -or -not $data.value) { continue }
        try { $c = $data.value | ConvertFrom-Json } catch { continue }
        $removed = @($c.removed | ForEach-Object { [int]$_ })
        $apps = @($c.added | ForEach-Object { [int]$_ } | Where-Object { $removed -notcontains $_ })
        $result.Add([ordered]@{ id = $c.id; name = $c.name; appIds = $apps; dynamic = [bool]$c.filterSpec })
    }
    return $result
}

# Every app in your Steam library: the client keeps artwork for each one, in folders or files
# named after its app ID.
function Get-LibraryAppIds($root) {
    $ids = New-Object System.Collections.Generic.HashSet[int]
    $dir = Join-Path $root "appcache\librarycache"
    if (Test-Path $dir) {
        Get-ChildItem $dir -ErrorAction SilentlyContinue | ForEach-Object {
            if ($_.Name -match '^(\d+)') { [void]$ids.Add([int]$matches[1]) }
        }
    }
    return @($ids)
}

function Get-AcfValue($text, $name) {
    $m = [regex]::Match($text, '"' + $name + '"\s+"([^"]*)"')
    if ($m.Success) { return $m.Groups[1].Value } else { return $null }
}

function Get-Steam {
    $reg = Get-ItemProperty "HKCU:\Software\Valve\Steam" -ErrorAction SilentlyContinue
    if (-not $reg -or -not $reg.SteamPath) { return [ordered]@{ installed = $false; games = @(); collections = @(); libraryAppIds = @() } }
    $root = $reg.SteamPath -replace "/", "\"
    # Libraries: Steam's own folder plus any others (other drives).
    $libraries = New-Object System.Collections.Generic.List[string]
    $libraries.Add($root)
    $folders = Join-Path $root "steamapps\libraryfolders.vdf"
    if (Test-Path $folders) {
        foreach ($m in [regex]::Matches([IO.File]::ReadAllText($folders), '"path"\s+"([^"]+)"')) {
            $p = $m.Groups[1].Value -replace "\\\\", "\"
            if (-not $libraries.Contains($p)) { $libraries.Add($p) }
        }
    }
    # Who's signed in.
    $user = $null
    $users = Join-Path $root "config\loginusers.vdf"
    if (Test-Path $users) {
        $text = [IO.File]::ReadAllText($users)
        foreach ($m in [regex]::Matches($text, '"(7656\d{13})"\s*\{([^{}]*)\}')) {
            $block = $m.Groups[2].Value
            $u = [ordered]@{
                steamId = $m.Groups[1].Value
                name = Get-AcfValue $block "PersonaName"
                account = Get-AcfValue $block "AccountName"
                recent = (Get-AcfValue $block "MostRecent") -eq "1"
            }
            if (-not $user -or $u.recent) { $user = $u }
        }
    }
    $played = @{}
    $collections = @()
    if ($user) {
        $accountId = [decimal]$user.steamId - [decimal]76561197960265728
        $played = Read-VdfApps (Join-Path $root "userdata\$accountId\config\localconfig.vdf")
        $collections = @(Get-Collections $root $accountId)
    }
    $games = @{}
    foreach ($lib in $libraries) {
        Get-ChildItem (Join-Path $lib "steamapps") -Filter "appmanifest_*.acf" -ErrorAction SilentlyContinue | ForEach-Object {
            $t = [IO.File]::ReadAllText($_.FullName)
            $id = Get-AcfValue $t "appid"
            if ($id) {
                $games[$id] = [ordered]@{
                    appId = [int]$id
                    name = Get-AcfValue $t "name"
                    installed = $true
                    sizeBytes = [double](Get-AcfValue $t "SizeOnDisk")
                    updated = [double](Get-AcfValue $t "LastUpdated")
                    library = $lib
                    minutes = 0
                    lastPlayed = 0
                }
            }
        }
    }
    foreach ($id in $played.Keys) {
        $p = $played[$id]
        if (-not $games.ContainsKey($id)) {
            if (-not ($id -match '^\d+$')) { continue }
            $games[$id] = [ordered]@{ appId = [int]$id; name = $null; installed = $false; sizeBytes = 0; updated = 0; library = $null; minutes = 0; lastPlayed = 0 }
        }
        if ($p.ContainsKey("Playtime")) { $games[$id].minutes = $p["Playtime"] }
        if ($p.ContainsKey("LastPlayed")) { $games[$id].lastPlayed = $p["LastPlayed"] }
    }
    # Steam's own tools (runtimes, redistributables) aren't games.
    $list = @($games.Values | Where-Object { -not ($_.name -match "^(Steamworks Common Redistributables|Steam Linux Runtime|Proton)") })
    return [ordered]@{
        installed = $true
        user = $user
        libraries = @($libraries)
        games = $list
        collections = $collections
        libraryAppIds = @(Get-LibraryAppIds $root)
    }
}

# ---------------------------------------------------------------- answering the phone

function Get-LanAddresses {
    @([System.Net.Dns]::GetHostAddresses([System.Net.Dns]::GetHostName()) |
        Where-Object { $_.AddressFamily -eq "InterNetwork" -and -not $_.ToString().StartsWith("127.") -and -not $_.ToString().StartsWith("169.254.") } |
        ForEach-Object { $_.ToString() })
}

function ConvertTo-Body($value) { return [Text.Encoding]::UTF8.GetBytes(($value | ConvertTo-Json -Depth 8 -Compress)) }

function Send-Response($stream, [int]$status, [byte[]]$body) {
    $reason = @{ 200 = "OK"; 401 = "Unauthorized"; 404 = "Not Found"; 500 = "Error" }[$status]
    $head = "HTTP/1.1 $status $reason`r`nContent-Type: application/json; charset=utf-8`r`nContent-Length: $($body.Length)`r`nConnection: close`r`n`r`n"
    $h = [Text.Encoding]::ASCII.GetBytes($head)
    $stream.Write($h, 0, $h.Length)
    $stream.Write($body, 0, $body.Length)
    $stream.Flush()
}

$config = Get-Config
$hello = [ordered]@{ app = "VividPulsePC"; version = $Version; name = $env:COMPUTERNAME; port = $Port }

if ($Once) {
    New-Item -ItemType Directory -Force -Path $Once | Out-Null
    ConvertTo-Json $hello -Depth 8 | Set-Content (Join-Path $Once "hello.json") -Encoding UTF8
    ConvertTo-Json (Get-Specs) -Depth 8 | Set-Content (Join-Path $Once "specs.json") -Encoding UTF8
    Get-Live | Out-Null
    Start-Sleep -Seconds 1
    ConvertTo-Json (Get-Live) -Depth 8 | Set-Content (Join-Path $Once "live.json") -Encoding UTF8
    ConvertTo-Json (Get-Steam) -Depth 8 | Set-Content (Join-Path $Once "steam.json") -Encoding UTF8
    exit 0
}

try { $Host.UI.RawUI.WindowTitle = "Vivid Pulse PC" } catch { }
Write-Host ""
Write-Host "  Vivid Pulse PC $Version" -ForegroundColor Cyan
Write-Host "  Reading this PC's specs and your Steam library..."
$specsCache = Get-Specs
$steamCache = Get-Steam
$steamAt = Get-Date
Get-Live | Out-Null

$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $Port)
try { $listener.Start() } catch {
    Write-Host "  Couldn't open port $Port. Is Vivid Pulse PC already running?" -ForegroundColor Red
    Read-Host "  Press Enter to close"
    exit 1
}
$udp = $null
try {
    $udp = New-Object System.Net.Sockets.UdpClient($DiscoveryPort)
    $udp.EnableBroadcast = $true
} catch { Write-Host "  (Finding it automatically won't work: port $DiscoveryPort is busy. Type the address in the app instead.)" -ForegroundColor Yellow }

Write-Host ""
Write-Host "  Ready. On your phone, open Vivid Pulse > PC > Find my PC" -ForegroundColor Green
Write-Host ""
Write-Host "      Code:     $($config.code)" -ForegroundColor White
Write-Host "      Address:  $((Get-LanAddresses) -join ', ')" -ForegroundColor White
Write-Host ""
Write-Host "  Phone and PC need to be on the same Wi-Fi. If Windows asks, allow access on private networks."
Write-Host "  Keep this window open (you can minimise it). Close it to stop."
Write-Host ""

while ($true) {
    # Someone looking for PCs on the network.
    if ($udp -and $udp.Available -gt 0) {
        try {
            $from = New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Any, 0)
            $bytes = $udp.Receive([ref]$from)
            if ([Text.Encoding]::ASCII.GetString($bytes) -eq "VIVIDPULSE_DISCOVER") {
                $reply = ConvertTo-Body $hello
                [void]$udp.Send($reply, $reply.Length, $from)
            }
        } catch { }
    }
    if (-not $listener.Pending()) { Start-Sleep -Milliseconds 40; continue }
    $client = $listener.AcceptTcpClient()
    try {
        $client.ReceiveTimeout = 3000
        $stream = $client.GetStream()
        $reader = New-Object System.IO.StreamReader($stream, [Text.Encoding]::ASCII, $false, 1024, $true)
        $request = $reader.ReadLine()
        # A connection that sends nothing (a port check): nothing to answer.
        if ([string]::IsNullOrEmpty($request)) { continue }
        $code = $null
        while ($true) {
            $line = $reader.ReadLine()
            if ([string]::IsNullOrEmpty($line)) { break }
            if ($line -match '^X-Pulse-Code:\s*(\d+)') { $code = $matches[1] }
        }
        $parts = ("" + $request).Split(" ")
        $path = if ($parts.Count -ge 2) { $parts[1] } else { "/" }
        $route = $path.Split("?")[0]
        $query = if ($path.Contains("?")) { $path.Substring($path.IndexOf("?") + 1) } else { "" }
        $from = $client.Client.RemoteEndPoint.ToString()
        if ($route -eq "/hello") {
            Send-Response $stream 200 (ConvertTo-Body $hello)
        } elseif ($code -ne $config.code) {
            Write-Host "  $(Get-Date -Format HH:mm:ss)  wrong code from $from"
            Send-Response $stream 401 (ConvertTo-Body @{ error = "Wrong code" })
        } else {
            switch ($route) {
                "/specs" { Send-Response $stream 200 (ConvertTo-Body $specsCache) }
                "/live" { Send-Response $stream 200 (ConvertTo-Body (Get-Live)) }
                "/steam" {
                    if ($query -match "refresh=1" -or ((Get-Date) - $steamAt).TotalMinutes -gt 10) { $steamCache = Get-Steam; $steamAt = Get-Date }
                    Send-Response $stream 200 (ConvertTo-Body $steamCache)
                }
                "/launch" {
                    if ($query -match "appid=(\d+)") {
                        $appId = [int]$matches[1]
                        try {
                            Start-Process "steam://rungameid/$appId" -ErrorAction Stop
                            Write-Host "  $(Get-Date -Format HH:mm:ss)  starting game $appId (asked from $from)"
                            Send-Response $stream 200 (ConvertTo-Body @{ started = $appId })
                        } catch {
                            Write-Host "  $(Get-Date -Format HH:mm:ss)  couldn't start game ${appId}: $($_.Exception.Message)" -ForegroundColor DarkYellow
                            Send-Response $stream 500 (ConvertTo-Body @{ error = "Steam couldn't start it" })
                        }
                    } else { Send-Response $stream 404 (ConvertTo-Body @{ error = "No game" }) }
                }
                default { Send-Response $stream 404 (ConvertTo-Body @{ error = "Not found" }) }
            }
        }
    } catch {
        Write-Host "  $(Get-Date -Format HH:mm:ss)  $($_.Exception.Message)" -ForegroundColor DarkYellow
    } finally {
        $client.Close()
    }
}
