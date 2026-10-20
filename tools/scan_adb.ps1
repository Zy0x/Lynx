$targets = 1..254 | ForEach-Object { "192.168.1.$_" }
$found = @()
foreach ($ip in $targets) {
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $iar = $tcp.BeginConnect($ip, 5555, $null, $null)
        if ($iar.AsyncWaitHandle.WaitOne(100, $false)) {
            $tcp.EndConnect($iar)
            Write-Host "FOUND: $ip" -ForegroundColor Green
            $found += $ip
        }
        $tcp.Close()
    } catch {}
}
if ($found.Count -eq 0) { Write-Host "None found on 192.168.1.0/24" }
