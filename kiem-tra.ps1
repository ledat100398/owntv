# Kiểm tra Glass Home trên tivi thật: chạy lần lượt các thao tác remote thường gặp, sau mỗi bước xem
# launcher có bị văng (crash) hay mất màn hình không, rồi bấm ngẫu nhiên 1.500 phím và so bộ nhớ trước/sau.
# Chụp ảnh màn hình ở các bước chính vào thư mục docs\. Chạy qua 7-KIEM-TRA.cmd.
# Không bấm OK vào cổng HDMI hay mục cài đặt nào, nên không đổi gì trên tivi.

. (Join-Path $PSScriptRoot 'tools\chung.ps1')
$pkg = 'io.github.ledat100398.tvhome'
$act = "$pkg/.MainActivity"
$docs = Join-Path $repo 'docs'
New-Item -ItemType Directory -Force $docs | Out-Null
$baoCao = Join-Path $saoLuu 'kiem-tra.txt'

ChuanBiPython
$ip = ChonIp; $env:TV_HOST = $ip
Write-Host 'KIỂM TRA GLASS HOME - khoảng 10 phút. Trong lúc chạy đừng bấm remote.' -ForegroundColor Yellow

$k = TV @("pm list packages $pkg")
if ($k["pm list packages $pkg"] -notmatch [regex]::Escape("package:$pkg")) { Loi 'Chưa cài Glass Home. Chạy 3-CAI-LAUNCHER.cmd trước.'; exit 1 }

function Phim([string[]]$ds, [double]$cho = 0.35) {
    $l = @()
    foreach ($p in $ds) {
        if ($p -like 'giu:*') { $l += "input keyevent --longpress KEYCODE_$($p.Substring(4))" }
        else { $l += "input keyevent KEYCODE_$p" }
        $l += "sleep $($cho.ToString([cultureinfo]::InvariantCulture))"   # "0.35", không phải "0,35" theo máy tiếng Việt
    }
    return $l
}
function Lap([string]$p, [int]$n) { return @(1..$n | ForEach-Object { $p }) }

function SoLanVang {
    $k = TV @('logcat -b crash -d')
    return ([regex]::Matches($k['logcat -b crash -d'], [regex]::Escape("Process: $pkg"))).Count
}
function DangHien {
    $q = 'dumpsys activity activities | grep -i resumedactivity'
    $k = TV @($q)
    return $k[$q] -match [regex]::Escape($pkg)
}
function Chup($ten) {
    TV @("screencap -p /sdcard/gh-$ten.png") | Out-Null
    $ErrorActionPreference = 'Continue'
    & $venvPy -I $tvPy --pull "/sdcard/gh-$ten.png" (Join-Path $docs "$ten.png") 2>&1 | Out-Null
    TV @("rm -f /sdcard/gh-$ten.png") | Out-Null
}
function UiDump {
    $k = TV @('uiautomator dump /sdcard/gh-ui.xml >/dev/null 2>&1; cat /sdcard/gh-ui.xml; rm -f /sdcard/gh-ui.xml')
    return $k['uiautomator dump /sdcard/gh-ui.xml >/dev/null 2>&1; cat /sdcard/gh-ui.xml; rm -f /sdcard/gh-ui.xml']
}
# Số lần bấm Xuống từ dòng đầu bảng Cài đặt để tới dòng "Màn hình chính"
function ViTriKieuManHinh {
    $xml = UiDump
    $ten = @('Hình ảnh', 'Âm thanh', 'Hẹn giờ tắt', 'Màn hình chính', 'Hiện lại app đã ẩn', 'Màn hình chính gốc', 'Tất cả cài đặt',
             'Picture', 'Sound', 'Sleep timer', 'Home screen', 'Show hidden apps', 'Original Home screen', 'All settings',
             '画面', '声音', '定时关机', '主屏幕样式', '显示已隐藏的应用', '原主屏幕', '全部设置')
    $dong = @()
    foreach ($m in [regex]::Matches($xml, 'text="([^"]*)"[^>]*bounds="\[(\d+),(\d+)\]')) {
        if ($ten -contains $m.Groups[1].Value) { $dong += [pscustomobject]@{ T = $m.Groups[1].Value; Y = [int]$m.Groups[3].Value } }
    }
    $dong = @($dong | Sort-Object Y)
    for ($i = 0; $i -lt $dong.Count; $i++) { if (@('Màn hình chính', 'Home screen', '主屏幕样式') -contains $dong[$i].T) { return $i } }
    return -1
}

$ketQua = @()
function Buoc($ten, [string[]]$lenh, [bool]$canHien = $true) {
    $truoc = SoLanVang
    if ($lenh) { TV $lenh | Out-Null }
    Start-Sleep -Milliseconds 600
    $sau = SoLanVang
    $hien = if ($canHien) { DangHien } else { $true }
    $dat = ($sau -eq $truoc) -and $hien
    $ghiChu = if ($sau -gt $truoc) { 'launcher bị văng' } elseif (-not $hien) { 'launcher không còn trên màn hình' } else { '' }
    $script:ketQua += [pscustomobject]@{ Buoc = $ten; Dat = $dat; GhiChu = $ghiChu }
    if ($dat) { Ok "  ĐẠT   $ten" } else { Loi "  LỖI   $ten - $ghiChu" }
}

Tieude 'Thao tác remote'
TV @('logcat -b crash -c') | Out-Null
Buoc 'Mở màn hình chính' @("am start -n $act", 'input keyevent KEYCODE_HOME', 'sleep 3')
Chup '1-home'
Buoc 'Phải liên tục: hết hàng sang hàng kế, app cuối về app đầu' (Phim (Lap 'DPAD_RIGHT' 14))
Buoc 'Trái liên tục: về app đầu, rung nhẹ ở app đầu' (Phim (Lap 'DPAD_LEFT' 16))
Buoc 'Xuống rồi lên' (Phim @('DPAD_DOWN', 'DPAD_UP'))
Buoc 'Giữ phím phải (lặp nhanh)' (Phim (Lap 'DPAD_RIGHT' 25) 0.09)
Buoc 'Về hàng đầu bằng Back' (Phim @('BACK', 'DPAD_LEFT', 'DPAD_LEFT', 'DPAD_LEFT', 'DPAD_LEFT', 'DPAD_LEFT'))
Buoc 'Lên thanh trên, chạy qua 3 nút, rung ở mép' (Phim @('DPAD_UP', 'DPAD_LEFT', 'DPAD_LEFT', 'DPAD_LEFT', 'DPAD_RIGHT', 'DPAD_RIGHT', 'DPAD_RIGHT', 'DPAD_UP'))
Chup '2-bar'
Buoc 'Mở bảng Cài đặt, lên xuống các dòng' ((Phim @('DPAD_CENTER') 1.2) + (Phim (Lap 'DPAD_DOWN' 8)) + (Phim (Lap 'DPAD_UP' 8)))
Chup '3-settings'
$n = ViTriKieuManHinh
if ($n -ge 0) {
    Buoc 'Bật đồng hồ lớn' ((Phim (Lap 'DPAD_DOWN' $n)) + (Phim @('DPAD_CENTER') 1) + (Phim @('BACK', 'DPAD_DOWN') 1))
    Chup '4-large-clock'
    Buoc 'Tắt đồng hồ lớn' ((Phim @('DPAD_UP') 0.8) + (Phim @('DPAD_CENTER') 1.2) + (Phim (Lap 'DPAD_DOWN' $n)) + (Phim @('DPAD_CENTER') 1) + (Phim @('BACK') 1))
} else {
    Loi '  BỎ QUA đồng hồ lớn: không tìm thấy dòng "Màn hình chính" trong bảng Cài đặt'
    TV (Phim @('BACK')) | Out-Null
}
Buoc 'Bảng Wi-Fi: mở' ((Phim @('DPAD_LEFT', 'DPAD_CENTER') 1.2) + (Phim @('DPAD_DOWN', 'DPAD_UP') 0.6))
Chup '5-wifi'
Buoc 'Bảng Wi-Fi: đóng bằng phím trái' (Phim @('DPAD_LEFT') 0.8)
Buoc 'Bảng Nguồn vào: mở, lên xuống, đóng bằng Back' ((Phim @('DPAD_LEFT', 'DPAD_CENTER') 1.2) + (Phim @('DPAD_DOWN', 'DPAD_DOWN', 'DPAD_DOWN', 'DPAD_UP')))
Chup '6-inputs'
Buoc 'Đóng bảng, về lưới' (Phim @('BACK', 'DPAD_DOWN') 0.8)
Buoc 'Giữ OK trên app: hiện bảng Dời/Ẩn/Thông tin' ((Phim @('giu:DPAD_CENTER') 1.2) + (Phim @('DPAD_DOWN', 'DPAD_DOWN', 'DPAD_UP', 'DPAD_UP')))
Chup '7-app-menu'
Buoc 'Đóng bảng bằng Back' (Phim @('BACK') 0.8)
Buoc 'Dời app rồi hủy bằng Back' ((Phim @('giu:DPAD_CENTER') 1.2) + (Phim @('DPAD_CENTER') 0.8) + (Phim @('DPAD_RIGHT', 'DPAD_DOWN', 'DPAD_LEFT', 'BACK') 0.5))
Buoc 'Dời app rồi đặt, sau đó dời về chỗ cũ' ((Phim @('giu:DPAD_CENTER') 1.2) + (Phim @('DPAD_CENTER') 0.8) + (Phim @('DPAD_RIGHT', 'DPAD_CENTER') 0.6) +
    (Phim @('giu:DPAD_CENTER') 1.2) + (Phim @('DPAD_CENTER') 0.8) + (Phim @('DPAD_LEFT', 'DPAD_CENTER') 0.6))
Buoc 'Home khi đang mở bảng' ((Phim @('DPAD_UP', 'DPAD_CENTER') 1) + (Phim @('HOME') 1.5))
Buoc 'Mở một app (hiệu ứng phóng)' ((Phim @('DPAD_DOWN', 'DPAD_CENTER') 0.5) + @('sleep 6')) $false
Buoc 'Quay về bằng Home (hiệu ứng thu về)' ((Phim @('HOME') 2.5))

Tieude 'Bấm ngẫu nhiên 1.500 phím'
$mem = "dumpsys meminfo $pkg"
$k = TV @($mem)
$pssTruoc = if ($k[$mem] -match 'TOTAL(?: PSS)?:\s+(\d+)') { [math]::Round([int]$Matches[1] / 1024) } else { -1 }
$monkey = "monkey -p $pkg -s 42 --throttle 140 --pct-touch 0 --pct-motion 0 --pct-trackball 0 --pct-syskeys 0 --pct-appswitch 0 --pct-flip 0 --pct-anyevent 0 --pct-nav 55 --pct-majornav 45 --ignore-security-exceptions -v 1500"
Buoc 'Bấm ngẫu nhiên 1.500 phím' @($monkey, 'sleep 2', 'input keyevent KEYCODE_HOME', 'sleep 2')
$k = TV @($mem)
$pssSau = if ($k[$mem] -match 'TOTAL(?: PSS)?:\s+(\d+)') { [math]::Round([int]$Matches[1] / 1024) } else { -1 }
Write-Host "  Bộ nhớ: trước $pssTruoc MB, sau $pssSau MB (ngưỡng 150 MB)"

Tieude 'Kết quả'
$loi = @($ketQua | Where-Object { -not $_.Dat })
$dong = @("Kiểm tra Glass Home - $(Get-Date -Format 'yyyy-MM-dd HH:mm')", '')
foreach ($r in $ketQua) { $dong += "$(if ($r.Dat) { 'ĐẠT ' } else { 'LỖI ' }) $($r.Buoc) $(if ($r.GhiChu) { '- ' + $r.GhiChu })" }
$dong += ''
$dong += "Bộ nhớ trước $pssTruoc MB, sau 1.500 phím $pssSau MB"
if ($loi.Count) {
    $k = TV @('logcat -b crash -d')
    $dong += ''; $dong += '--- Nhật ký lỗi ---'; $dong += $k['logcat -b crash -d']
}
$dong | Set-Content $baoCao -Encoding UTF8
if ($loi.Count) { Loi "$($loi.Count) bước lỗi. Báo cáo: sao-luu\kiem-tra.txt" } else { Ok "Tất cả $($ketQua.Count) bước đều đạt. Báo cáo: sao-luu\kiem-tra.txt" }
if ($pssSau -gt 150) { Loi "Bộ nhớ sau khi bấm ($pssSau MB) vượt ngưỡng 150 MB." }
Write-Host "Ảnh màn hình: docs\"
