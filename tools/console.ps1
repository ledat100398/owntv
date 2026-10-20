# Chữ tiếng Việt trong cửa sổ đen: nhiều máy Windows còn để phông Raster/Lucida, không có đủ dấu
# (hiện "đ?t", "m?ng"). Đổi cửa sổ sang Consolas; đổi không được thì in không dấu cho dễ đọc.
# Nạp bằng: . (Join-Path $PSScriptRoot 'console.ps1')
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
$global:KhongDau = $false
try {
    if (-not ('OwnTV.Con' -as [type])) {
        Add-Type -Namespace OwnTV -Name Con -MemberDefinition @'
[StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
public struct FontInfo {
    public uint cbSize; public uint nFont; public short X; public short Y;
    public int FontFamily; public int FontWeight;
    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string FaceName;
}
[DllImport("kernel32.dll", SetLastError = true)] public static extern IntPtr GetStdHandle(int n);
[DllImport("kernel32.dll", SetLastError = true)] public static extern bool GetCurrentConsoleFontEx(IntPtr h, bool max, ref FontInfo f);
[DllImport("kernel32.dll", SetLastError = true)] public static extern bool SetCurrentConsoleFontEx(IntPtr h, bool max, ref FontInfo f);
[DllImport("kernel32.dll", SetLastError = true)] public static extern bool GetConsoleMode(IntPtr h, out uint mode);
[DllImport("kernel32.dll", SetLastError = true)] public static extern bool SetConsoleMode(IntPtr h, uint mode);
'@
    }
    # Tắt "Quick Edit": lỡ bấm chuột vào cửa sổ là Windows dừng script lại (tiêu đề hiện "Select") cho tới khi
    # bấm Enter, trông như bị treo.
    $vao = [OwnTV.Con]::GetStdHandle(-10); $m = 0
    if ([OwnTV.Con]::GetConsoleMode($vao, [ref]$m)) { [void][OwnTV.Con]::SetConsoleMode($vao, [uint32](([int64]$m -bor 0x80) -band 4294967231)) }
    $tot = @('Consolas', 'Cascadia Mono', 'Cascadia Code', 'Courier New')
    $h = [OwnTV.Con]::GetStdHandle(-11)
    $f = New-Object 'OwnTV.Con+FontInfo'
    $f.cbSize = [Runtime.InteropServices.Marshal]::SizeOf($f)
    # Windows Terminal không cho đọc phông theo cách này, nhưng nó hiện tiếng Việt sẵn rồi.
    if ([OwnTV.Con]::GetCurrentConsoleFontEx($h, $false, [ref]$f) -and $tot -notcontains $f.FaceName) {
        $f.FaceName = 'Consolas'; $f.FontFamily = 54; $f.FontWeight = 400; $f.X = 0
        if ($f.Y -lt 16) { $f.Y = 18 }
        [void][OwnTV.Con]::SetCurrentConsoleFontEx($h, $false, [ref]$f)
        [void][OwnTV.Con]::GetCurrentConsoleFontEx($h, $false, [ref]$f)
        if ($tot -notcontains $f.FaceName) { $global:KhongDau = $true }
    }
} catch { }

function BoDau([string]$s) {
    $s = $s -creplace 'đ', 'd' -creplace 'Đ', 'D' -replace '›', '>' -replace '[–—]', '-' -replace '…', '...'
    $n = $s.Normalize([Text.NormalizationForm]::FormD)
    $b = New-Object System.Text.StringBuilder
    foreach ($ch in $n.ToCharArray()) {
        if ([Globalization.CharUnicodeInfo]::GetUnicodeCategory($ch) -ne 'NonSpacingMark') { [void]$b.Append($ch) }
    }
    return $b.ToString().Normalize([Text.NormalizationForm]::FormC)
}

if ($global:KhongDau) {
    function global:Write-Host {
        param([Parameter(Position = 0, ValueFromRemainingArguments = $true)]$Object, $ForegroundColor, [switch]$NoNewline)
        $p = @{ Object = (BoDau (($Object | ForEach-Object { "$_" }) -join ' ')) }
        if ($ForegroundColor) { $p.ForegroundColor = $ForegroundColor }
        if ($NoNewline) { $p.NoNewline = $true }
        Microsoft.PowerShell.Utility\Write-Host @p
    }
    function global:Read-Host {
        param([Parameter(Position = 0)]$Prompt)
        Microsoft.PowerShell.Utility\Read-Host (BoDau "$Prompt")
    }
}
