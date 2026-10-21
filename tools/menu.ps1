# Menu duy nhất của OwnTV. Mở bằng OwnTV.cmd ở thư mục gốc.
. (Join-Path $PSScriptRoot 'console.ps1')
$ps = Join-Path $PSHOME 'powershell.exe'
function Chay($tep, $cheDo) {
    # Start-Process, not "& powershell ...": called inside a function whose result is used, PowerShell would
    # capture the child's whole output as the return value, so nothing showed on screen but the questions.
    $tepDu = Join-Path $PSScriptRoot $tep
    $p = Start-Process -FilePath $ps -NoNewWindow -Wait -PassThru `
        -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$tepDu`"", $cheDo)
    $ma = $p.ExitCode
    if ($ma -ne 0) {
        Write-Host ''
        Write-Host "  Dừng lại với mã lỗi $ma (stopped, exit code $ma)." -ForegroundColor Red
        Write-Host "  Chụp cửa sổ này gửi lên GitHub Issues (take a screenshot and open an issue)."
    }
    return $ma
}
while ($true) {
    Clear-Host
    Write-Host ''
    Write-Host '  OwnTV - Your TV. Your apps. Nothing else.' -ForegroundColor Cyan
    Write-Host '  ------------------------------------------------------------'
    Write-Host '  1. Cài OwnTV và đặt làm màn hình chính    (install + set as home)'
    Write-Host '  2. Trả lại màn hình gốc                    (back to the stock home)'
    Write-Host ''
    Write-Host '  3. Tắt app chạy ngầm - tivi Sony           (debloat, optional)'
    Write-Host '  4. Bật lại các app đã tắt ở mục 3          (undo 3)'
    Write-Host '  5. Đo độ mượt                              (measure performance)'
    Write-Host '  6. Kiểm tra lỗi nút Home, ghi ra tệp gửi đi (Home button report)'
    Write-Host ''
    Write-Host '  0. Thoát                                   (exit)'
    Write-Host '  ------------------------------------------------------------'
    Write-Host '  Bấm số (press a number): ' -NoNewline
    $k = [Console]::ReadKey($true).KeyChar
    if ('1', '2', '3', '4', '5', '6', '0' -notcontains [string]$k) { continue }
    Write-Host $k
    Write-Host ''
    switch ([string]$k) {
        '1' { if ((Chay 'launcher.ps1' 'cai') -eq 0) { $env:HOME_DONGY = '1'; [void](Chay 'launcher.ps1' 'home'); $env:HOME_DONGY = $null } }
        '2' { [void](Chay 'launcher.ps1' 'goc') }
        '3' { [void](Chay 'tv-sony.ps1' 'toiuu') }
        '4' { [void](Chay 'tv-sony.ps1' 'khoiphuc') }
        '5' { [void](Chay 'launcher.ps1' 'do') }
        '6' { [void](Chay 'launcher.ps1' 'kham') }
        '0' { exit 0 }
    }
    Write-Host ''
    Write-Host '  Bấm phím bất kỳ để về menu (press any key)...' -NoNewline
    [void][Console]::ReadKey($true)
}
