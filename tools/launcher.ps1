# Launcher mới cho tivi Sony (Google TV): dựng, cài, đặt làm màn hình chính, trả lại màn gốc, đo hiệu năng.
# Chạy qua OwnTV.cmd ở thư mục gốc (menu 1, 2, 5).
param([ValidateSet('cai', 'home', 'goc', 'do', 'kham')][string]$CheDo = 'cai')

. (Join-Path $PSScriptRoot 'chung.ps1')
$pkg      = 'io.github.ledat100398.tvhome'
$act      = "$pkg/.MainActivity"
$dream    = "$pkg/.ClockDream"
$stocks   = @('com.google.android.apps.tv.launcherx', 'com.google.android.tvlauncher')
$ghiGoc   = Join-Path $saoLuu 'man-goc.txt'
# Google TV: khi màn gốc vắng mặt, trình cài đặt ban đầu (setupwraith) tự chiếm nút Home để "sửa" lại.
# Phải gỡ cả nó cho người dùng 0 thì Home mới về launcher mới. Trả lại được bằng install-existing.
$phuTro   = 'com.google.android.tungsten.setupwraith'
$apk      = Join-Path $repo 'launcher\app\build\outputs\apk\debug\app-debug.apk'

# ---------- JDK 17 ----------
function TimJdk {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        $rel = Join-Path $env:JAVA_HOME 'release'
        if ((Test-Path $rel) -and ((Get-Content $rel -Raw) -match 'JAVA_VERSION="(1[7-9]|[2-9]\d)')) { return $env:JAVA_HOME }
    }
    $cho = @('C:\Program Files\Microsoft\jdk-17*', 'C:\Program Files\Microsoft\jdk-21*', 'C:\Program Files\Eclipse Adoptium\jdk-17*',
             'C:\Program Files\Eclipse Adoptium\jdk-21*', 'C:\Program Files\Java\jdk-17*', 'C:\Program Files\Java\jdk-21*')
    foreach ($c in $cho) {
        $d = Get-ChildItem -Path $c -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
        if ($d -and (Test-Path (Join-Path $d.FullName 'bin\java.exe'))) { return $d.FullName }
    }
    $as = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path (Join-Path $as 'bin\java.exe')) { return $as }
    return $null
}

function ChuanBiJdk {
    $j = TimJdk
    if (-not $j) {
        Tieude 'Cài Java 17 (chỉ lần đầu, khoảng 180 MB)'
        if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
            Loi 'Máy không có winget. Cài Java 17 ở https://learn.microsoft.com/java/openjdk/download rồi chạy lại.'
            exit 1
        }
        winget install -e --id Microsoft.OpenJDK.17 --accept-package-agreements --accept-source-agreements
        $j = TimJdk
        if (-not $j) { Loi 'Chưa thấy Java sau khi cài. Đóng cửa sổ, mở lại OwnTV.cmd và chọn 1.'; exit 1 }
    }
    $env:JAVA_HOME = $j
    $env:Path = (Join-Path $j 'bin') + ';' + $env:Path
}

# ---------- Android SDK (chỉ phần cần để dựng, không cần Android Studio) ----------
function ChuanBiSdk {
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $env:ANDROID_HOME = $sdk
    $can = (Test-Path (Join-Path $sdk 'platforms\android-34\android.jar')) -and (Test-Path (Join-Path $sdk 'build-tools\34.0.0'))
    if (-not $can) {
        Tieude 'Tải bộ dựng Android (chỉ lần đầu, khoảng 400 MB)'
        $mgr = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
        if (-not (Test-Path $mgr)) {
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            $ProgressPreference = 'SilentlyContinue'
            New-Item -ItemType Directory -Force $taiTam | Out-Null
            $zip = Join-Path $taiTam 'android-cmdline-tools.zip'
            $tmp = Join-Path $taiTam 'android-cmdline-tools'
            Invoke-WebRequest 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' -OutFile $zip -UseBasicParsing
            XoaThuMuc $tmp
            Expand-Archive -Force $zip $tmp
            New-Item -ItemType Directory -Force (Join-Path $sdk 'cmdline-tools') | Out-Null
            $dich = Join-Path $sdk 'cmdline-tools\latest'
            XoaThuMuc $dich
            Move-Item (Join-Path $tmp 'cmdline-tools') $dich
            XoaTep $zip; XoaThuMuc $tmp
        }
        $ErrorActionPreference = 'Continue'
        (1..30 | ForEach-Object { 'y' }) | & $mgr --licenses | Out-Null
        & $mgr 'platforms;android-34' 'build-tools;34.0.0' 'platform-tools'
        $ErrorActionPreference = 'Stop'
        if (-not (Test-Path (Join-Path $sdk 'platforms\android-34\android.jar'))) { Loi 'Tải bộ dựng chưa xong (kiểm tra mạng) rồi chạy lại.'; exit 1 }
    }
    $esc = -join (($sdk -replace '\\', '/').ToCharArray() | ForEach-Object { if ([int]$_ -gt 127) { '\u{0:x4}' -f [int]$_ } else { [string]$_ } })
    "sdk.dir=$esc" | Set-Content -Encoding ASCII (Join-Path $repo 'launcher\local.properties')
}

function DungApk {
    Tieude 'Dựng launcher (lần đầu vài phút, các lần sau nhanh)'
    $ErrorActionPreference = 'Continue'
    & (Join-Path $repo 'launcher\gradlew.bat') -p (Join-Path $repo 'launcher') assembleDebug --console=plain -q
    $code = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($code -ne 0 -or -not (Test-Path $apk)) { Loi 'Dựng lỗi. Chụp màn hình cửa sổ này và mở issue trên GitHub.'; exit 1 }
    Ok 'Dựng xong.'
}

function GocDangBat {
    foreach ($s in $stocks) {
        $k = TV @("pm list packages -e $s")
        if ($k["pm list packages -e $s"] -match [regex]::Escape("package:$s")) { return $s }
    }
    return $null
}

function GocCoCai {
    foreach ($s in $stocks) {
        $k = TV @("pm list packages $s")
        if ($k["pm list packages $s"] -match [regex]::Escape("package:$s")) { return $s }
    }
    return $null
}

# Mọi màn hình chính khác trên máy (của Google, của hãng tivi hay box). Tivi nào có màn của hãng ưu tiên cao
# thì khởi động lại là nó giành lại nút Home, nên phải gỡ hết cho người dùng 0, không chỉ màn của Google.
function ManChinhKhac {
    $q = 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
    $k = TV @($q)
    $ds = @()
    foreach ($d in ($k[$q] -split "`n")) {
        $d = $d.Trim()
        if ($d -notmatch '^([\w.]+)/') { continue }
        $g = $Matches[1]
        # Bỏ qua launcher này, màn dự phòng của Cài đặt và trình thiết lập ban đầu (xử lý riêng ở dưới).
        if ($g -eq $pkg -or $g -eq $phuTro -or $g -match 'settings|provision|setupwizard') { continue }
        if ($ds -notcontains $g) { $ds += $g }
    }
    foreach ($s in $stocks) { if ($ds -notcontains $s -and (GocCoCai) -eq $s) { $ds += $s } }
    return $ds
}

# Dự phòng cho tivi không chịu bỏ màn gốc: dịch vụ trợ năng của OwnTV nhận nút Home và mở OwnTV
# (giống app đổi chức năng nút). Bật/tắt bằng cài đặt hệ thống, không cần gỡ gì.
$nutHome = "$pkg/$pkg.HomeKeyService"
function BatNutHome([bool]$bat) {
    $q = 'settings get secure enabled_accessibility_services'
    $k = TV @($q)
    $ds = @("$($k[$q])".Trim() -split ':' | Where-Object { $_ -and $_ -ne 'null' -and $_ -notmatch [regex]::Escape("$pkg/") })
    if ($bat) { $ds += $nutHome }
    if ($ds) {
        TV @("settings put secure enabled_accessibility_services '$($ds -join ':')'", 'settings put secure accessibility_enabled 1') | Out-Null
    } else {
        TV @('settings delete secure enabled_accessibility_services', 'settings put secure accessibility_enabled 0') | Out-Null
    }
}

# =====================================================================
ChuanBiPython
$ip = ChonIp; $env:TV_HOST = $ip

if ($CheDo -eq 'cai') {
    Write-Host 'CÀI LAUNCHER MỚI - chạy song song với màn hình gốc, chưa thay nút Home.' -ForegroundColor Yellow
    # Bình thường tải bản dựng sẵn trên GitHub (vài MB, vài giây). Chỉ khi không tải được, hoặc đặt
    # TUDUNG=1 để dựng từ mã nguồn đang có, mới cần Java 17 và bộ dựng Android (khoảng 600 MB).
    $sanCo = $false
    if ($env:TUDUNG -ne '1') {
        Tieude 'Tải OwnTV.apk'
        $tai = Join-Path $saoLuu 'OwnTV.apk'
        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            $ProgressPreference = 'SilentlyContinue'
            Invoke-WebRequest 'https://github.com/ledat100398/owntv/releases/latest/download/OwnTV.apk' -OutFile $tai -UseBasicParsing
            if ((Get-Item -LiteralPath $tai).Length -gt 500KB) { $apk = $tai; $sanCo = $true; Ok 'Đã tải bản mới nhất.' }
        } catch { }
        if (-not $sanCo) { Write-Host 'Không tải được, chuyển sang tự dựng từ mã nguồn.' }
    }
    if (-not $sanCo) {
        ChuanBiJdk
        ChuanBiSdk
        DungApk
    }
    Tieude 'Cài lên tivi'
    Write-Host 'Nhìn lên tivi: nếu hiện "Cho phép gỡ lỗi USB?", tick "Luôn cho phép" rồi OK (có 2 phút).' -ForegroundColor Yellow
    $ErrorActionPreference = 'Continue'
    & $venvPy -I (Join-Path $repo 'tools\b64push.py') $apk /data/local/tmp/tvhome.apk
    $ok = $LASTEXITCODE -eq 0
    $ErrorActionPreference = 'Stop'
    if (-not $ok) { Loi 'Gửi tệp lên tivi lỗi. Kiểm tra tivi còn bật rồi chạy lại.'; exit 1 }
    $cai = 'pm install -r /data/local/tmp/tvhome.apk'
    $k = TV @($cai)
    if ($k[$cai] -match 'UPDATE_INCOMPATIBLE|SIGNATURES') {
        # Bản cũ do máy tính tự dựng (khóa riêng của từng máy); bản mới dùng một khóa chung cho mọi người.
        Write-Host 'Bản OwnTV trên tivi được dựng bằng khóa cũ nên không cập nhật đè được.'
        if (-not (Hoi 'Gỡ bản cũ rồi cài bản mới? (thứ tự app đã xếp sẽ về mặc định)' $true)) { TV @('rm -f /data/local/tmp/tvhome.apk') | Out-Null; exit 1 }
        $k = TV @("pm uninstall $pkg", $cai)
    }
    if ($k[$cai] -notmatch 'Success') { Loi "Cài lỗi: $("$($k[$cai])".Trim())"; TV @('rm -f /data/local/tmp/tvhome.apk') | Out-Null; exit 1 }
    $k = TV @('rm -f /data/local/tmp/tvhome.apk',
              "pm grant $pkg android.permission.ACCESS_FINE_LOCATION", "pm grant $pkg android.permission.RECORD_AUDIO",
              'settings get secure screensaver_components', 'settings get secure assistant',
              'settings get secure voice_interaction_service')
    Ok 'Đã cài.'
    # Nút micro trên điều khiển: tivi chưa có trợ lý nào (Google TV sau khi gỡ màn gốc) thì để tìm bằng
    # giọng nói của OwnTV nhận nút này. Tivi đã có Google Assistant chạy được thì giữ nguyên.
    $troLy = "$($k['settings get secure assistant'])".Trim(); $vis = "$($k['settings get secure voice_interaction_service'])".Trim()
    if ((-not $troLy -or $troLy -eq 'null') -and (-not $vis -or $vis -eq 'null')) {
        TV @("settings put secure assistant $pkg/.VoiceActivity") | Out-Null
        Ok 'Nút micro: bấm là mở tìm bằng giọng nói của OwnTV.'
    }
    # Bản thử trước đây mang mã com.gathin.tvhome: chuyển màn hình chính sang bản mới rồi gỡ bản cũ.
    $cuPkg = 'com.gathin.tvhome'
    $q = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
    $k2 = TV @("pm list packages $cuPkg", $q)
    if ($k2["pm list packages $cuPkg"] -match [regex]::Escape("package:$cuPkg")) {
        $lenh = @()
        if ($k2[$q] -match [regex]::Escape($cuPkg)) { $lenh += "cmd package set-home-activity --user 0 $act" }
        $lenh += "pm uninstall $cuPkg"
        TV $lenh | Out-Null
        Ok 'Đã chuyển sang bản mới và gỡ bản thử cũ.'
    }
    $cu = $k['settings get secure screensaver_components'].Trim()
    if ($cu -match [regex]::Escape($cuPkg)) { $cu = '' }
    if ($cu -ne $dream -and (Hoi 'Dùng màn chờ Đồng hồ của launcher mới?' $true)) {
        if (-not (Test-Path (Join-Path $saoLuu 'man-cho-cu.txt'))) { Set-Content (Join-Path $saoLuu 'man-cho-cu.txt') $cu }
        TV @("settings put secure screensaver_components $dream", 'settings put secure screensaver_enabled 1') | Out-Null
    }
    TV @("am start -n $act") | Out-Null
    Write-Host ''
    Ok 'Đã cài OwnTV, đang mở trên tivi.'
    exit 0
}

if ($CheDo -eq 'home') {
    Write-Host 'ĐẶT LAUNCHER MỚI LÀM MÀN HÌNH CHÍNH (mở tivi và nút Home đều ra launcher mới).' -ForegroundColor Yellow
    Write-Host 'Màn gốc của Google được gỡ khỏi người dùng (không xóa khỏi tivi); màn của hãng tivi giữ nguyên.'
    Write-Host 'Muốn về như cũ: mở OwnTV.cmd, chọn 2.'
    $k = TV @("pm list packages $pkg")
    if ($k["pm list packages $pkg"] -notmatch [regex]::Escape("package:$pkg")) { Loi 'Chưa cài OwnTV. Mở OwnTV.cmd, chọn 1.'; exit 1 }
    if (-not ($env:HOME_DONGY -eq '1' -or (Hoi 'Làm luôn?' $false))) { exit 0 }
    # Chỉ gỡ màn chính của Google. Màn chính của hãng tivi (Coocaa, TCL, Xiaomi...) thường kiêm luôn việc tắt
    # tivi bằng nút nguồn, gỡ đi là nút nguồn hết tác dụng, nên để nguyên: dịch vụ chuyển nút Home lo phần đó.
    $khac = @(ManChinhKhac)
    $goGi = @($khac | Where-Object { $stocks -contains $_ })
    $hang = @($khac | Where-Object { $stocks -notcontains $_ })
    if ($hang) { Write-Host "  Giữ nguyên màn chính của hãng: $($hang -join ', ') (nút Home vẫn ra OwnTV)." }
    # Bản cũ từng gỡ màn chính của hãng: trả lại, để nút nguồn trên điều khiển tắt được tivi.
    $daGhi = if (Test-Path $ghiGoc) { @(Get-Content $ghiGoc | ForEach-Object { $_.Trim() } | Where-Object { $_ }) } else { @() }
    $kDs = TV @('pm list packages -u', 'pm list packages')
    $coHet = @("$($kDs['pm list packages -u'])" -split "`n" | ForEach-Object { $_.Trim() -replace '^package:', '' } | Where-Object { $_ })
    $dangCo = @("$($kDs['pm list packages'])" -split "`n" | ForEach-Object { $_.Trim() -replace '^package:', '' } | Where-Object { $_ })
    $traHang = @($coHet | Where-Object {
        $dangCo -notcontains $_ -and $stocks -notcontains $_ -and $_ -ne $phuTro -and $_ -ne $pkg -and
        ($_ -match 'launcher|tvhome|homeshell|desktop' -or $daGhi -contains $_) })
    if ($traHang) {
        # Chỉ những gói thật sự là màn hình chính (có hoạt động HOME), không đụng app đã dọn bằng mục 3.
        $kHome = TV @($traHang | ForEach-Object { "dumpsys package $_ | grep -c category.HOME" })
        $traHang = @($traHang | Where-Object { "$($kHome["dumpsys package $_ | grep -c category.HOME"])".Trim() -match '^[1-9]' })
    }
    if ($traHang) {
        $tra = @(); foreach ($g in $traHang) { $tra += "cmd package install-existing $g"; $tra += "pm enable $g" }
        TV $tra | Out-Null
        Ok "Đã trả lại màn chính của hãng (bản cũ từng gỡ): $($traHang -join ', ')"
    }
    $goc = $goGi | Select-Object -First 1
    # Ghi lại cả các màn đã gỡ từ lần trước, để mục 2 trả lại đủ.
    if ($goGi) { @($daGhi + $goGi | Select-Object -Unique) | Set-Content $ghiGoc }
    # Sony bật lại app bị "tắt" mỗi lần khởi động lại, nên dùng gỡ cho người dùng 0 (giữ được, trả lại được).
    # Android 10 trở lên lưu màn hình chính dưới dạng "vai trò" (role); đặt cả hai cho chắc.
    $lenh = @("cmd package set-home-activity --user 0 $act", "cmd role add-role-holder --user 0 android.app.role.HOME $pkg 0")
    if ($goGi) { Write-Host "  Màn hình chính sẽ gỡ tạm: $($goGi -join ', ')" }
    foreach ($g in $goGi) { $lenh += "pm uninstall -k --user 0 $g" }
    $k0 = TV @("pm list packages $phuTro")
    $coPhu = $k0["pm list packages $phuTro"] -match [regex]::Escape("package:$phuTro")
    if ($coPhu) { $lenh += "pm uninstall -k --user 0 $phuTro" }
    $lenh += "cmd package set-home-activity --user 0 $act"
    $kGo = TV $lenh
    BatNutHome $true          # thêm lớp giữ nút Home, phòng khi tivi tự đưa màn gốc trở lại
    $lenh = @('input keyevent KEYCODE_HOME')
    $lenh += 'sleep 3'
    $xem = 'dumpsys activity activities | grep -i resumedactivity'
    $lenh += $xem
    $k = TV $lenh
    foreach ($g in $goGi) {
        if ($kGo["pm uninstall -k --user 0 $g"] -notmatch 'Success') { Loi "Không gỡ được màn gốc $g`: $("$($kGo["pm uninstall -k --user 0 $g"])".Trim())" }
    }
    Write-Host ''
    if ($k[$xem] -match [regex]::Escape($pkg)) {
        Ok 'Xong. Launcher mới là màn hình chính: mở tivi hay bấm Home đều ra nó.'
        Write-Host 'Tắt tivi bằng nút nguồn rồi mở lại để chắc chắn. Nếu vẫn về màn cũ, chụp cửa sổ này và mở issue trên GitHub.'
    } else {
        Loi 'Gỡ màn gốc chưa đủ trên tivi này. Thử cách chuyển nút Home sang OwnTV...'
        $tra = @("cmd package install-existing $phuTro")
        foreach ($g in $goGi) { $tra += "cmd package install-existing $g"; $tra += "pm enable $g" }
        TV $tra | Out-Null
        # Tắt rồi bật lại để dịch vụ khởi động lại và thấy màn gốc vừa trả về.
        BatNutHome $false; Start-Sleep -Seconds 2; BatNutHome $true
        # Phím Home giả lập qua adb không đi qua dịch vụ trợ năng (phím thật trên điều khiển thì có), và Android cũ
        # hoãn mở app 5 giây sau Home, nên chờ lâu hơn và coi dịch vụ đã chạy là đủ.
        $k3 = TV @('input keyevent KEYCODE_HOME', 'sleep 7', $xem, 'dumpsys accessibility')
        $daChay = "$($k3['dumpsys accessibility'])" -match 'HomeKeyService'
        if ($k3[$xem] -match [regex]::Escape($pkg) -or $daChay) {
            Ok 'Xong. Màn gốc vẫn còn trên tivi, nhưng nút Home trên điều khiển giờ mở OwnTV.'
            Write-Host 'Bấm thử nút Home trên điều khiển, rồi tắt tivi bằng nút nguồn và mở lại để chắc chắn.'
            Write-Host 'Muốn về như cũ: mở OwnTV.cmd, chọn 2.'
            exit 0
        }
        BatNutHome $false
        Start-Sleep -Seconds 2
        Loi 'Vẫn chưa được. Đang trả lại màn gốc...'
        if ($goc) {
            $q = "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $goc"
            $tra = @("cmd package install-existing $phuTro")
            foreach ($g in $goGi) { $tra += "cmd package install-existing $g"; $tra += "pm enable $g" }
            $k2 = TV ($tra + $q)
            $comp = ($k2[$q] -split "`n" | Where-Object { $_ -match '/' } | Select-Object -Last 1)
            $lui = @()
            if ($comp) { $lui += "cmd package set-home-activity --user 0 $($comp.Trim())" }
            $lui += 'input keyevent KEYCODE_HOME'
            TV $lui | Out-Null
        }
        Write-Host "Đã trả lại. Tivi báo: $("$($k[$xem])".Trim())"
        Write-Host 'Chụp màn hình cửa sổ này và mở issue trên GitHub.'
        exit 2
    }
    exit 0
}

if ($CheDo -eq 'goc') {
    Write-Host 'TRẢ LẠI MÀN HÌNH CHÍNH GỐC CỦA TIVI.' -ForegroundColor Yellow
    $daGo = if (Test-Path $ghiGoc) { @(Get-Content $ghiGoc | ForEach-Object { $_.Trim() } | Where-Object { $_ }) } else { @() }
    if ($daGo) {
        $tra = @(); foreach ($g in $daGo) { $tra += "cmd package install-existing $g"; $tra += "pm enable $g" }
        TV $tra | Out-Null
    }
    $goc = if ($daGo) { $daGo[0] } else { GocCoCai }
    if (-not $goc) {
        # Chạy từ máy khác (không có sao lưu) và màn gốc đã bị gỡ khỏi người dùng: thử cài lại từng màn gốc đã biết.
        foreach ($s in $stocks) {
            $k = TV @("cmd package install-existing $s")
            if ($k["cmd package install-existing $s"] -match 'installed') { $goc = $s; break }
        }
    }
    if (-not $goc) { Loi 'Không tìm thấy màn hình gốc trên tivi.'; exit 1 }
    $k = TV @("cmd package install-existing $phuTro", "cmd package install-existing $goc", "pm enable $goc", "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $goc")
    $comp = ($k["cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME $goc"] -split "`n" | Where-Object { $_ -match '/' } | Select-Object -Last 1)
    $lenh = @()
    if ($comp) { $lenh += "cmd package set-home-activity --user 0 $($comp.Trim())" }
    $cho = Join-Path $saoLuu 'man-cho-cu.txt'
    if (Test-Path $cho) {
        $c = (Get-Content $cho -Raw).Trim()
        if ($c -and $c -ne 'null') { $lenh += "settings put secure screensaver_components $c" } else { $lenh += 'settings delete secure screensaver_components' }
    }
    BatNutHome $false
    Start-Sleep -Seconds 2
    $lenh += 'input keyevent KEYCODE_HOME'
    $kTl = TV @('settings get secure assistant')
    if ("$($kTl['settings get secure assistant'])" -match [regex]::Escape($pkg)) { $lenh = @('settings delete secure assistant') + $lenh }
    TV $lenh | Out-Null
    Ok "Đã bật lại $goc. Bấm Home để kiểm tra. Launcher mới vẫn còn trong hàng ứng dụng, gỡ trong Cài đặt nếu không dùng."
    exit 0
}

if ($CheDo -eq 'do') {
    Write-Host 'ĐO HIỆU NĂNG LAUNCHER (so với ngưỡng mục tiêu).' -ForegroundColor Yellow
    $k = TV @("pm list packages $pkg")
    if ($k["pm list packages $pkg"] -notmatch [regex]::Escape("package:$pkg")) { Loi 'Chưa cài launcher mới.'; exit 1 }

    Tieude 'Thời gian mở'
    $k = TV @("am force-stop $pkg", "am start -W -n $act")
    $lanh = if ($k["am start -W -n $act"] -match 'TotalTime:\s*(\d+)') { [int]$Matches[1] } else { -1 }
    Start-Sleep 3
    TV @('am start -a android.settings.SETTINGS') | Out-Null
    Start-Sleep 3
    $k = TV @("am start -W -n $act")
    $am = if ($k["am start -W -n $act"] -match 'TotalTime:\s*(\d+)') { [int]$Matches[1] } else { -1 }
    Write-Host "Mở lạnh: $lanh ms   Mở lại: $am ms"

    Tieude 'Độ mượt'
    TV @("dumpsys gfxinfo $pkg reset") | Out-Null
    Write-Host 'Cầm remote lướt màn hình chính khoảng 30 giây: sang trái, sang phải, xuống, lên, giữ phím một lúc.'
    Read-Host 'Xong thì bấm Enter ở đây' | Out-Null
    $k = TV @("dumpsys gfxinfo $pkg", "dumpsys meminfo $pkg")
    $g = $k["dumpsys gfxinfo $pkg"]
    $tong = if ($g -match 'Total frames rendered:\s*(\d+)') { [int]$Matches[1] } else { 0 }
    $giat = if ($g -match 'Janky frames:\s*\d+\s*\(([\d.]+)%\)') { [double]$Matches[1] } else { -1 }
    $p95 = if ($g -match '95th percentile:\s*(\d+)ms') { [int]$Matches[1] } else { -1 }
    $m = $k["dumpsys meminfo $pkg"]
    $pss = if ($m -match 'TOTAL(?: PSS)?:\s+(\d+)') { [math]::Round([int]$Matches[1] / 1024) } else { -1 }

    function Dong($ten, $gt, $don, $nguong, [bool]$dat) {
        $mau = if ($gt -lt 0) { 'Gray' } elseif ($dat) { 'Green' } else { 'Red' }
        $kq = if ($gt -lt 0) { 'không đo được' } elseif ($dat) { 'ĐẠT' } else { 'CHƯA ĐẠT' }
        Write-Host ("  {0,-26} {1,8} {2,-4} (mục tiêu {3})  {4}" -f $ten, $gt, $don, $nguong, $kq) -ForegroundColor $mau
    }
    Tieude 'Kết quả'
    Dong 'Mở lạnh' $lanh 'ms' '< 1500 ms' ($lanh -ge 0 -and $lanh -lt 1500)
    Dong 'Mở lại (bấm Home)' $am 'ms' '< 300 ms' ($am -ge 0 -and $am -lt 300)
    Dong "Khung giật ($tong khung)" $giat '%' '< 5 %' ($giat -ge 0 -and $giat -lt 5)
    Dong 'Khung chậm nhất 95%' $p95 'ms' '< 16 ms' ($p95 -ge 0 -and $p95 -lt 16)
    Dong 'Bộ nhớ (PSS)' $pss 'MB' '< 150 MB' ($pss -ge 0 -and $pss -lt 150)
    $csv = Join-Path $saoLuu 'do-hieu-nang.csv'
    if (-not (Test-Path $csv)) { 'thoi_gian,mo_lanh_ms,mo_lai_ms,khung,giat_pct,p95_ms,pss_mb' | Set-Content $csv -Encoding UTF8 }
    "$(Get-Date -Format 'yyyy-MM-dd HH:mm'),$lanh,$am,$tong,$giat,$p95,$pss" | Add-Content $csv -Encoding UTF8
    Write-Host ''
    Write-Host 'Đã ghi vào sao-luu\do-hieu-nang.csv để so các lần đo. Có dòng CHƯA ĐẠT thì chụp màn này và mở issue trên GitHub.'
    exit 0
}

if ($CheDo -eq 'kham') {
    Write-Host 'KIỂM TRA NÚT HOME - ghi lại tivi làm gì khi bấm Home, để biết vì sao vẫn về màn cũ.' -ForegroundColor Yellow
    $hoi = @('getprop ro.product.manufacturer', 'getprop ro.product.model', 'getprop ro.build.version.release',
        "dumpsys package $pkg | grep versionName",
        'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME',
        'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME',
        'cmd role get-role-holders --user 0 android.app.role.HOME',
        'settings get secure enabled_accessibility_services', 'settings get secure accessibility_enabled',
        'dumpsys accessibility | grep -iE "Bound services|Enabled services|HomeKeyService|Crashed"')
    $k1 = TV $hoi
    TV @('logcat -c') | Out-Null
    Write-Host ''
    Write-Host 'Cầm điều khiển của tivi, bấm nút Home MỘT lần. Đợi màn hình đứng yên (khoảng 5 giây).' -ForegroundColor Yellow
    Read-Host 'Xong thì bấm Enter ở đây' | Out-Null
    $nhat = 'logcat -d -v time | grep -iE "START u0|Displayed|HomeKeyService|keycode=3|KEYCODE_HOME|AccessibilityInputFilter|KeyboardInterceptor" | tail -40'
    $k2 = TV @('dumpsys activity activities | grep -iE "resumedactivity|mFocusedApp"', 'dumpsys window | grep -E "mCurrentFocus|mFocusedApp"', $nhat)
    $bc = @("OwnTV - kiem tra nut Home - $(Get-Date -Format 'yyyy-MM-dd HH:mm')", '')
    foreach ($q in $hoi) { $bc += "### $q"; $bc += "$($k1[$q])".Trim(); $bc += '' }
    $bc += '##### SAU KHI BAM HOME #####'
    foreach ($q in $k2.Keys) { $bc += "### $q"; $bc += "$($k2[$q])".Trim(); $bc += '' }
    $tep = Join-Path ([Environment]::GetFolderPath('Desktop')) 'OwnTV-kiem-tra.txt'
    try { $bc | Set-Content -LiteralPath $tep -Encoding UTF8 } catch { $tep = Join-Path $saoLuu 'OwnTV-kiem-tra.txt'; $bc | Set-Content -LiteralPath $tep -Encoding UTF8 }
    Ok "Đã ghi ra $tep"
    Write-Host 'Gửi tệp này (hoặc chụp màn hình nó) cho người hỗ trợ, hoặc dán vào GitHub Issues.'
    Start-Process notepad.exe -ArgumentList "`"$tep`""
    exit 0
}
