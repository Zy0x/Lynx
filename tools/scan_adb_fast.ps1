$ips = 1..254 | ForEach-Object { "192.168.1.$_" }
$tasks = foreach ($ip in $ips) {
    [System.Threading.Tasks.Task]::Run([Action]{
        try {
            $client = New-Object System.Net.Sockets.TcpClient
            $iar = $client.BeginConnect($ip, 5555, $null, $null)
            if ($iar.AsyncWaitHandle.WaitOne(350, $false)) {
                $client.EndConnect($iar)
                Write-Output $ip
            }
            $client.Close()
        } catch {}
    })
}
[System.Threading.Tasks.Task]::WaitAll($tasks)
