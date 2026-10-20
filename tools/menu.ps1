# Menu duy nhất của OwnTV. Mở bằng OwnTV.cmd ở thư mục gốc.
. (Join-Path $PSScriptRoot 'console.ps1')
$ps = Join-Path $PSHOME 'powershell.exe'
function Chay($tep, $cheDo) {
    & $ps -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot $tep) $cheDo
    return $LASTEXITCODE
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
    Write-Host ''
    Write-Host '  0. Thoát                                   (exit)'
    Write-Host '  ------------------------------------------------------------'
    Write-Host '  Bấm số (press a number): ' -NoNewline
    $k = [Console]::ReadKey($true).KeyChar
    if ('1', '2', '3', '4', '5', '0' -notcontains [string]$k) { continue }
    Write-Host $k
    Write-Host ''
    switch ([string]$k) {
        '1' { if ((Chay 'launcher.ps1' 'cai') -eq 0) { $env:HOME_DONGY = '1'; [void](Chay 'launcher.ps1' 'home'); $env:HOME_DONGY = $null } }
        '2' { [void](Chay 'launcher.ps1' 'goc') }
        '3' { [void](Chay 'tv-sony.ps1' 'toiuu') }
        '4' { [void](Chay 'tv-sony.ps1' 'khoiphuc') }
        '5' { [void](Chay 'launcher.ps1' 'do') }
        '0' { exit 0 }
    }
    Write-Host ''
    Write-Host '  Bấm phím bất kỳ để về menu (press any key)...' -NoNewline
    [void][Console]::ReadKey($true)
}
