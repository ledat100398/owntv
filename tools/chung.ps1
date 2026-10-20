# Dùng chung cho tv-sony.ps1 và launcher.ps1: đường dẫn, Python + thư viện ADB, chọn IP, chạy lệnh trên tivi.
# Nạp bằng: . (Join-Path $PSScriptRoot 'chung.ps1')
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'console.ps1')

$repo   = Split-Path -Parent $PSScriptRoot
$venvPy = Join-Path $repo 'tools\venv\Scripts\python.exe'
$tvPy   = Join-Path $repo 'tools\tv.py'
$saoLuu = Join-Path $repo 'sao-luu'
New-Item -ItemType Directory -Force $saoLuu | Out-Null
# Tải tạm vào ngay thư mục này, không dùng %TEMP%: tên người dùng có dấu cách (vd "Duy Vu") khiến %TEMP%
# thành dạng rút gọn C:\Users\DUYVU~1\..., và Remove-Item của PowerShell 5.1 báo "không tồn tại" với dạng đó.
$taiTam = Join-Path $repo 'tools\tai-tam'
function XoaThuMuc($p) {
    if (Test-Path -LiteralPath $p) { [IO.Directory]::Delete((Resolve-Path -LiteralPath $p).ProviderPath, $true) }
}
function XoaTep($p) { if (Test-Path -LiteralPath $p) { [IO.File]::Delete((Resolve-Path -LiteralPath $p).ProviderPath) } }

function Tieude($s) { Write-Host ''; Write-Host "== $s ==" -ForegroundColor Cyan }
function Loi($s)    { Write-Host $s -ForegroundColor Red }
function Ok($s)     { Write-Host $s -ForegroundColor Green }

# Chế độ tự động (TUDONG=1): mọi câu hỏi lấy câu trả lời mặc định, IP lấy từ TV_IP. Dùng khi chạy không cần người trả lời.
$TuDong = $env:TUDONG -eq '1'

function Hoi($cauHoi, [bool]$macDinh) {
    if ($TuDong) { Write-Host "$cauHoi -> $(if ($macDinh) { 'có' } else { 'không' }) (tự động)"; return $macDinh }
    $goiY = if ($macDinh) { '[C/k]' } else { '[c/K]' }
    while ($true) {
        $t = (Read-Host "$cauHoi $goiY").Trim().ToLower()
        if ($t -eq '') { return $macDinh }
        if ($t -in @('c', 'co', 'có', 'y')) { return $true }
        if ($t -in @('k', 'khong', 'không', 'n')) { return $false }
    }
}

# ---------- Python + thư viện ADB ----------
function TimPython {
    # Python cài theo người dùng nằm ở đây; cửa sổ mở trước lúc cài chưa thấy nó trong PATH.
    foreach ($d in @("$env:LOCALAPPDATA\Programs\Python\Python313", "$env:LOCALAPPDATA\Programs\Python\Python312",
                     "$env:LOCALAPPDATA\Programs\Python\Python311", "C:\Program Files\Python312", "C:\Program Files\Python313")) {
        $p = Join-Path $d 'python.exe'
        if (Test-Path $p) { return @{ exe = $p; a = @() } }
    }
    $ErrorActionPreference = 'Continue'
    foreach ($c in @(@{ exe = 'py'; a = @('-3') }, @{ exe = 'python'; a = @() })) {
        try {
            $exe = $c.exe; $a = $c.a
            $v = & $exe @a --version 2>&1
            if ("$v" -match 'Python 3') { $ErrorActionPreference = 'Stop'; return $c }
        } catch { }
    }
    $ErrorActionPreference = 'Stop'
    return $null
}

function ChuanBiPython {
    if (Test-Path $venvPy) { return }
    Tieude 'Cài công cụ (chỉ lần đầu)'
    $c = TimPython
    if (-not $c) {
        Loi 'Máy chưa có Python 3.'
        if ((Get-Command winget -ErrorAction SilentlyContinue) -and (Hoi 'Cài Python tự động bằng winget?' $true)) {
            winget install -e --id Python.Python.3.12 --scope user --accept-package-agreements --accept-source-agreements
            $c = TimPython
        }
        if (-not $c) {
            Write-Host 'Tải ở https://www.python.org/downloads/ , lúc cài nhớ tick "Add python.exe to PATH", rồi chạy lại.'
            exit 1
        }
    }
    $py = $c.exe; $pyArgs = $c.a
    & $py @pyArgs -m venv (Join-Path $repo 'tools\venv')
    & $venvPy -m pip install -q --disable-pip-version-check -r (Join-Path $repo 'tools\requirements.txt')
    if ($LASTEXITCODE -ne 0) { Loi 'Cài thư viện lỗi (kiểm tra mạng).'; XoaThuMuc (Join-Path $repo 'tools\venv'); exit 1 }
    Ok 'Đã cài xong công cụ.'
}

# ---------- Kết nối tivi ----------
function CongMo($ip) {
    $c = New-Object System.Net.Sockets.TcpClient
    try { $r = $c.BeginConnect($ip, 5555, $null, $null); return ($r.AsyncWaitHandle.WaitOne(3000) -and $c.Connected) }
    catch { return $false } finally { $c.Close() }
}

# Tivi Android 11 trở lên thường không mở cổng 5555 nữa mà dùng "Gỡ lỗi không dây" có mã ghép đôi.
# Dùng adb chính hãng để ghép đôi một lần, rồi "adb tcpip 5555" mở lại cổng 5555 (tới lần khởi động lại sau).
function TimAdb {
    foreach ($a in @((Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'), (Join-Path $repo 'tools\platform-tools\adb.exe'))) {
        if (Test-Path $a) { return $a }
    }
    Write-Host 'Tải công cụ ADB của Google (khoảng 7 MB)...'
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $ProgressPreference = 'SilentlyContinue'
    New-Item -ItemType Directory -Force $taiTam | Out-Null
    $zip = Join-Path $taiTam 'platform-tools.zip'
    Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip -UseBasicParsing
    Expand-Archive -Force $zip (Join-Path $repo 'tools')
    XoaTep $zip
    return (Join-Path $repo 'tools\platform-tools\adb.exe')
}

function SoCong($s) { if ("$s" -match '(\d{2,5})\s*$') { return $Matches[1] } return $null }

function GhepKhongDay($ip) {
    Tieude 'Kết nối bằng Gỡ lỗi không dây (tivi Android 11 trở lên)'
    Write-Host 'Trên tivi: Tùy chọn nhà phát triển > Gỡ lỗi không dây > bật lên, rồi chọn "Ghép nối thiết bị bằng mã".'
    Write-Host 'Tivi hiện một mã 6 số và một dòng "IP:cổng". Giữ nguyên màn đó.'
    $ErrorActionPreference = 'Continue'
    try { $adb = TimAdb } catch { Loi 'Không tải được ADB (kiểm tra mạng).'; return $false }
    $cp = SoCong (Read-Host '  Cổng ghép nối (số sau dấu hai chấm trên màn có mã, vd 37123)')
    $ma = ((Read-Host '  Mã ghép nối 6 số') -replace '\D', '')
    if (-not $cp -or -not $ma) { Loi 'Thiếu cổng hoặc mã.'; return $false }
    & $adb pair "$($ip):$cp" $ma
    Write-Host 'Quay về màn "Gỡ lỗi không dây": dòng "Địa chỉ IP và cổng" có một cổng khác.'
    $cc = SoCong (Read-Host '  Cổng ở dòng "Địa chỉ IP và cổng" (vd 41567)')
    if (-not $cc) { Loi 'Thiếu cổng.'; return $false }
    & $adb connect "$($ip):$cc"
    & $adb -s "$($ip):$cc" tcpip 5555
    Start-Sleep -Seconds 4
    & $adb kill-server 2>$null | Out-Null
    $ErrorActionPreference = 'Stop'
    if (CongMo $ip) { Ok 'Đã mở cổng 5555. Tivi sẽ hỏi "Cho phép gỡ lỗi USB?": tick "Luôn cho phép" > OK.'; return $true }
    Loi 'Vẫn chưa mở được cổng 5555.'
    return $false
}

function ChonIp {
    $f = Join-Path $saoLuu 'tv-ip.txt'
    $cu = if (Test-Path $f) { (Get-Content $f -Raw).Trim() } else { '' }
    if ($env:TV_IP) { $cu = $env:TV_IP.Trim() }
    if ($TuDong -and $cu) {
        if (CongMo $cu) { Set-Content $f $cu; return $cu }
        Loi "Không vào được tivi $cu (cổng 5555). Kiểm tra tivi bật và đã bật Gỡ lỗi USB."; exit 1
    }
    while ($true) {
        $ip = if ($cu) { $x = (Read-Host "Địa chỉ IP của tivi [$cu]").Trim(); if ($x) { $x } else { $cu } }
              else { (Read-Host 'Địa chỉ IP của tivi (Cài đặt > Mạng > trạng thái mạng, vd 192.168.1.50)').Trim() }
        # Gõ dễ dãi cũng nhận: dấu phẩy thay dấu chấm (bàn phím số), khoảng trắng, ":5555", "http://".
        $ip = ($ip -replace '^\w+://', '' -replace '[,，。]', '.' -replace '\s', '' -replace ':\d+$', '' -replace '/.*$', '')
        $ok = $ip -match '^\d{1,3}(\.\d{1,3}){3}$' -and -not ($ip.Split('.') | Where-Object { [int]$_ -gt 255 })
        if (-not $ok) { Loi "IP không đúng dạng: '$ip'. Gõ 4 cụm số cách nhau dấu chấm, vd 192.168.1.50"; $cu = ''; continue }
        if (CongMo $ip) { Set-Content $f $ip; return $ip }
        Loi "Không vào được tivi $ip (cổng 5555)."
        if (-not $TuDong -and (Hoi '  Tivi có mục "Gỡ lỗi không dây" (Android 11 trở lên)? Thử kết nối bằng mã ghép đôi' $true)) {
            if (GhepKhongDay $ip) { Set-Content $f $ip; return $ip }
        }
        Write-Host '  Kiểm tra: tivi đang bật, cùng mạng với máy tính (không dùng Wi-Fi khách), đã bật Gỡ lỗi USB'
        Write-Host '  và cả Gỡ lỗi qua mạng nếu tivi có mục đó. Đúng IP thì tivi sẽ hỏi "Cho phép gỡ lỗi USB?".'
        Write-Host '  (Cài đặt > Tùy chọn thiết bị > Giới thiệu > bấm Bản dựng 7 lần > Tùy chọn nhà phát triển).'
        $cu = $ip
        if (-not (Hoi 'Thử lại?' $true)) { exit 1 }
    }
}

# Chạy nhiều lệnh trên tivi trong 1 lần kết nối, trả về bảng [lệnh -> kết quả].
function TV([string[]]$lenh, [switch]$KhongThoat) {
    # PowerShell 5.1 biến dòng stderr của python thành lỗi khi đang ở chế độ Stop, nên tạm nới ra.
    $ErrorActionPreference = 'Continue'
    $raw = $lenh | & $venvPy -I $tvPy --json 2>&1
    if ($LASTEXITCODE -ne 0) {
        if ($KhongThoat) { return $null }
        Loi 'Không kết nối được tivi.'
        Write-Host ($raw | Select-Object -Last 3 | Out-String)
        Write-Host 'Nếu tivi hiện "Cho phép gỡ lỗi USB?" mà bạn chưa bấm kịp: tick "Luôn cho phép" > OK, rồi chạy lại.'
        exit 1
    }
    $kq = @{}
    $json = $raw | Where-Object { $_ -is [string] -and $_.StartsWith('[') } | Select-Object -Last 1
    if (-not $json) { if ($KhongThoat) { return $null }; Loi 'Tivi trả về dữ liệu lạ.'; exit 1 }
    foreach ($o in ($json | ConvertFrom-Json)) { $kq[$o.cmd] = "$($o.out)" }
    return $kq
}

function RamTrong($meminfo) {
    if ($meminfo -match 'MemAvailable:\s+(\d+)') { return [math]::Round([int64]$Matches[1] / 1024) }
    return 0
}
function RamTong($meminfo) {
    if ($meminfo -match 'MemTotal:\s+(\d+)') { return [math]::Round([int64]$Matches[1] / 1024) }
    return 0
}
function DsGoi($out) { @($out -split "`n" | ForEach-Object { ($_ -replace '^package:', '').Trim() } | Where-Object { $_ }) }

function ChoKhoiDongLai($ip) {
    Write-Host 'Đang chờ tivi khởi động lại (tối đa 4 phút)...'
    Start-Sleep 25
    for ($i = 0; $i -lt 45; $i++) {
        if (CongMo $ip) {
            $k = TV @('getprop sys.boot_completed') -KhongThoat
            if ($k -and $k['getprop sys.boot_completed'].Trim() -eq '1') { Start-Sleep 40; return $true }
        }
        Start-Sleep 5
    }
    return $false
}
