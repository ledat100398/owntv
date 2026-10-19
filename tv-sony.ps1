# Tối ưu tivi Sony qua Wi-Fi: tắt app chạy ngầm, quảng cáo, theo dõi.
# Gỡ app khỏi người dùng (pm uninstall -k --user 0): bản cài gốc vẫn nằm trong tivi, khôi phục bằng install-existing.
# Không dùng pm disable-user vì tivi Sony tự bật lại app bị tắt mỗi lần khởi động lại.
# Chạy qua 1-TOI-UU-TV.cmd (tối ưu) hoặc 2-KHOI-PHUC.cmd (bật lại mọi thứ đã tắt).
param([ValidateSet('toiuu', 'khoiphuc')][string]$CheDo = 'toiuu')

. (Join-Path $PSScriptRoot 'tools\chung.ps1')
$dsGoi  = Join-Path $repo 'examples\sony-bravia-vn\goi-tat.tsv'
# =====================================================================
if ($CheDo -eq 'khoiphuc') {
    ChuanBiPython
    Tieude 'Khôi phục'
    $logs = @(Get-ChildItem $saoLuu -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'da-doi.txt') } | Sort-Object Name -Descending)
    if (-not $logs) { Loi 'Chưa có lần tối ưu nào để khôi phục.'; exit 1 }
    for ($i = 0; $i -lt $logs.Count; $i++) { Write-Host "  [$($i+1)] lần chạy $($logs[$i].Name)" }
    $chon = (Read-Host 'Khôi phục lần nào? [1 = mới nhất]').Trim(); if (-not $chon) { $chon = '1' }
    if ($chon -notmatch '^\d+$' -or [int]$chon -lt 1 -or [int]$chon -gt $logs.Count) { Loi "Chọn số từ 1 đến $($logs.Count)."; exit 1 }
    $log = Join-Path $logs[[int]$chon - 1].FullName 'da-doi.txt'
    $ip = ChonIp; $env:TV_HOST = $ip
    $lenh = @()
    foreach ($d in (Get-Content $log -Encoding UTF8)) {
        $c = $d -split "`t"
        if ($c[0] -eq 'goi') { $lenh += "cmd package install-existing $($c[1])"; $lenh += "pm enable $($c[1])" }
        elseif ($c[0] -eq 'caidat') {
            if ($c[3] -eq 'null') { $lenh += "settings delete $($c[1]) $($c[2])" } else { $lenh += "settings put $($c[1]) $($c[2]) $($c[3])" }
        }
    }
    $k = TV $lenh
    $loi = @($k.Keys | Where-Object { $_ -like 'pm enable*' -and $k[$_] -notmatch 'new state' })
    Ok "Đã trả lại $(@($lenh | Where-Object { $_ -like 'pm enable*' }).Count - $loi.Count) app và cài đặt cũ."
    foreach ($x in $loi) { Loi "  Lỗi: $x -> $($k[$x].Trim())" }
    if (Hoi 'Khởi động lại tivi cho chắc?' $true) { TV @('reboot') | Out-Null; Ok 'Tivi đang khởi động lại.' }
    exit 0
}

# =====================================================================
Write-Host 'TỐI ƯU TIVI SONY - app được gỡ khỏi người dùng, bản gốc vẫn trong tivi; chạy 2-KHOI-PHUC.cmd là trả lại hết.' -ForegroundColor Yellow
ChuanBiPython
$ip = ChonIp; $env:TV_HOST = $ip

Tieude 'Kết nối'
Write-Host 'Nhìn lên tivi: nếu hiện "Cho phép gỡ lỗi USB?" thì tick "Luôn cho phép từ máy tính này" > OK.'
$props = 'ro.product.manufacturer', 'ro.product.model', 'ro.build.version.release', 'ro.build.version.sdk', 'ro.build.display.id'
$k = TV (@($props | ForEach-Object { "getprop $_" }) + @('pm list packages', 'pm list packages -d', 'pm list packages -3', 'cat /proc/meminfo',
    'getprop', 'settings list global', 'settings list secure', 'settings list system',
    'settings get global window_animation_scale', 'settings get global transition_animation_scale',
    'settings get global animator_duration_scale', 'settings get secure screensaver_enabled',
    'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'))
$hang = $k['getprop ro.product.manufacturer'].Trim(); $model = $k['getprop ro.product.model'].Trim()
$tatCa = DsGoi $k['pm list packages']; $daTatSan = DsGoi $k['pm list packages -d']; $appTai = DsGoi $k['pm list packages -3']
$ramTruoc = RamTrong $k['cat /proc/meminfo']; $ramTong = RamTong $k['cat /proc/meminfo']
Ok "Đã kết nối: $hang $model, Android $($k['getprop ro.build.version.release'].Trim()), RAM $ramTong MB, đang trống $ramTruoc MB."
if ($hang -notmatch 'Sony') {
    Loi "Tivi này không phải Sony ($hang). Danh sách gói được soạn cho Sony."
    if (-not (Hoi 'Vẫn tiếp tục? (chỉ tắt gói nào có mặt trên tivi)' $false)) { exit 0 }
}

# Sao lưu trước khi đổi bất cứ gì
$thuMuc = Join-Path $saoLuu (Get-Date -Format 'yyyyMMdd-HHmmss')
New-Item -ItemType Directory -Force $thuMuc | Out-Null
$k['getprop'] | Set-Content (Join-Path $thuMuc 'getprop.txt') -Encoding UTF8
$tatCa -join "`r`n" | Set-Content (Join-Path $thuMuc 'goi-tat-ca.txt') -Encoding UTF8
$daTatSan -join "`r`n" | Set-Content (Join-Path $thuMuc 'goi-da-tat-tu-truoc.txt') -Encoding UTF8
"$hang $model`r`nMàn hình chính: $($k['cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'].Trim())" |
    Set-Content (Join-Path $thuMuc 'thong-tin.txt') -Encoding UTF8
foreach ($ns in 'global', 'secure', 'system') { $k["settings list $ns"] | Set-Content (Join-Path $thuMuc "caidat-$ns.txt") -Encoding UTF8 }
Ok "Đã sao lưu vào sao-luu\$(Split-Path $thuMuc -Leaf)"

# ---------- Chọn gói ----------
$tenNhom = @{ '1' = 'Theo dõi & quảng cáo'; '2' = 'Chế độ trưng bày cửa hàng'; '3' = 'Gửi báo lỗi'; '4' = 'App cài sẵn ít dùng'
              '5' = 'Trợ lý giọng nói (mất nút micro tìm kiếm)'; '6' = 'Chromecast & chiếu màn hình điện thoại' }
$goi = @(Get-Content $dsGoi -Encoding UTF8 | Where-Object { $_ -and -not $_.StartsWith('#') } | ForEach-Object {
    $c = $_ -split "`t"; [pscustomobject]@{ Goi = $c[0]; Nhom = $c[1]; MoTa = $c[2] }
} | Where-Object { $tatCa -contains $_.Goi -and $daTatSan -notcontains $_.Goi })

$seTat = New-Object System.Collections.ArrayList
Tieude 'Chọn thứ cần tắt'
Write-Host 'Enter = theo mặc định. Gõ t để chọn từng app trong nhóm.'
foreach ($n in '1', '2', '3', '4', '5', '6') {
    $trong = @($goi | Where-Object { $_.Nhom -eq $n })
    if (-not $trong) { continue }
    Write-Host ''
    Write-Host "Nhóm $n - $($tenNhom[$n]) ($($trong.Count) app):" -ForegroundColor Yellow
    foreach ($g in $trong) { Write-Host "   - $($g.MoTa)" }
    $md = if ([int]$n -le 4) { 'c' } else { 'k' }
    if ($TuDong) { $t = $md; Write-Host "  -> $(if ($md -eq 'c') { 'tắt' } else { 'giữ' }) (tự động)" }
    else { $t = (Read-Host "  Tắt nhóm này? c = có / k = không / t = từng cái [mặc định $md]").Trim().ToLower(); if (-not $t) { $t = $md } }
    if ($t -eq 'c') { $trong | ForEach-Object { [void]$seTat.Add($_) } }
    elseif ($t -eq 't') { foreach ($g in $trong) { if (Hoi "    Tắt: $($g.MoTa)?" ([int]$n -le 4)) { [void]$seTat.Add($g) } } }
}

$khac = @($appTai | Where-Object { $_ -ne 'io.github.ledat100398.tvhome' } | Sort-Object)
if ($khac) {
    Write-Host ''
    Write-Host 'App tải về / của hãng đang có (bạn tự quyết):' -ForegroundColor Yellow
    for ($i = 0; $i -lt $khac.Count; $i++) { Write-Host ("   [{0,2}] {1}" -f ($i + 1), $khac[$i]) }
    $so = if ($TuDong) { '' } else { (Read-Host '  Gõ số các app muốn tắt, cách nhau dấu phẩy (Enter = giữ hết)').Trim() }
    foreach ($x in ($so -split '[,\s]+' | Where-Object { $_ -match '^\d+$' })) {
        $j = [int]$x - 1; if ($j -ge 0 -and $j -lt $khac.Count) { [void]$seTat.Add([pscustomobject]@{ Goi = $khac[$j]; Nhom = '7'; MoTa = $khac[$j] }) }
    }
}

# ---------- Cài đặt hiển thị ----------
$doiCaiDat = @()
Write-Host ''
# Hiệu ứng chuyển cảnh giữ nguyên: tắt đi thì mở app bị cắt cảnh thô và giao diện mới mất chuyển động mượt.
if (Hoi 'Tắt màn hình chờ (screensaver)?' $true) {
    $doiCaiDat += , @('secure', 'screensaver_enabled', $k['settings get secure screensaver_enabled'].Trim(), '0')
}

if (-not $seTat.Count -and -not $doiCaiDat.Count) { Write-Host 'Không chọn gì, không đổi gì.'; exit 0 }
Tieude 'Xác nhận'
Write-Host "Sẽ tắt $($seTat.Count) app và đổi $($doiCaiDat.Count) cài đặt."
if (-not (Hoi 'Làm luôn?' $true)) { Write-Host 'Đã hủy, chưa đổi gì.'; exit 0 }

# ---------- Thực hiện ----------
$log = Join-Path $thuMuc 'da-doi.txt'
$lenh = @($seTat | ForEach-Object { "pm uninstall -k --user 0 $($_.Goi)" }) + @($doiCaiDat | ForEach-Object { "settings put $($_[0]) $($_[1]) $($_[3])" })
$k2 = TV $lenh
$dong = @(); $loi = @()
foreach ($g in $seTat) {
    $o = $k2["pm uninstall -k --user 0 $($g.Goi)"]
    if ($o -match 'Success') { $dong += "goi`t$($g.Goi)`t$($g.MoTa)" } else { $loi += "$($g.MoTa): $($o.Trim())" }
}
foreach ($d in $doiCaiDat) { $dong += "caidat`t$($d[0])`t$($d[1])`t$($d[2])" }
$dong | Set-Content $log -Encoding UTF8
Ok "Đã tắt $($seTat.Count - $loi.Count) app. Danh sách để khôi phục: sao-luu\$(Split-Path $thuMuc -Leaf)\da-doi.txt"
foreach ($x in $loi) { Loi "  Không tắt được (để nguyên): $x" }

# ---------- Khởi động lại & kiểm tra ----------
if (Hoi 'Khởi động lại tivi và đo lại RAM?' $true) {
    TV @('reboot') | Out-Null
    if (ChoKhoiDongLai $ip) {
        $k3 = TV @('pm list packages', 'cat /proc/meminfo')
        $con = DsGoi $k3['pm list packages']
        $bat = @($seTat | Where-Object { $con -contains $_.Goi })
        $ramSau = RamTrong $k3['cat /proc/meminfo']
        Tieude 'Kết quả'
        Write-Host "RAM trống: trước $ramTruoc MB  ->  sau $ramSau MB (trên tổng $ramTong MB)"
        Write-Host '(Số "trước" đo lúc tivi đã chạy lâu, "sau" đo ngay sau khởi động; dùng vài ngày rồi chạy lại để so cho công bằng.)'
        if ($bat) { Loi "Có $($bat.Count) app tự bật lại: $(($bat | ForEach-Object { $_.Goi }) -join ', ')" } else { Ok 'Mọi app đã tắt vẫn tắt sau khởi động lại.' }
    } else { Loi 'Chờ quá lâu chưa thấy tivi. Mở tivi lên rồi chạy lại để kiểm tra.' }
}
Write-Host ''
Write-Host 'Xong. Có gì trục trặc: chạy 2-KHOI-PHUC.cmd. Dùng ổn vài ngày thì tắt "Gỡ lỗi USB" trên tivi cho an toàn.' -ForegroundColor Yellow
