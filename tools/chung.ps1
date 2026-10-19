# Dùng chung cho tv-sony.ps1 và launcher.ps1: đường dẫn, Python + thư viện ADB, chọn IP, chạy lệnh trên tivi.
# Nạp bằng: . (Join-Path $PSScriptRoot 'tools\chung.ps1')
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
$OutputEncoding = New-Object System.Text.UTF8Encoding $false

$repo   = Split-Path -Parent $PSScriptRoot
$venvPy = Join-Path $repo 'tools\venv\Scripts\python.exe'
$tvPy   = Join-Path $repo 'tools\tv.py'
$saoLuu = Join-Path $repo 'sao-luu'
New-Item -ItemType Directory -Force $saoLuu | Out-Null

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
    if ($LASTEXITCODE -ne 0) { Loi 'Cài thư viện lỗi (kiểm tra mạng).'; Remove-Item -Recurse -Force (Join-Path $repo 'tools\venv'); exit 1 }
    Ok 'Đã cài xong công cụ.'
}

# ---------- Kết nối tivi ----------
function CongMo($ip) {
    $c = New-Object System.Net.Sockets.TcpClient
    try { $r = $c.BeginConnect($ip, 5555, $null, $null); return ($r.AsyncWaitHandle.WaitOne(3000) -and $c.Connected) }
    catch { return $false } finally { $c.Close() }
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
        if ($ip -notmatch '^\d{1,3}(\.\d{1,3}){3}$') { Loi 'IP không đúng dạng.'; $cu = ''; continue }
        if (CongMo $ip) { Set-Content $f $ip; return $ip }
        Loi "Không vào được tivi $ip (cổng 5555)."
        Write-Host '  Kiểm tra: tivi đang bật, cùng mạng Wi-Fi với máy tính, đã bật Gỡ lỗi USB'
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
