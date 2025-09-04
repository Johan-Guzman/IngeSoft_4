# PowerShell script para automatizar pruebas (corregido y robusto)
# - WorkingDirectory correcto para evitar "Acceso denegado" al escribir logs
# - No pre-crea server_log.csv (lo crea el servidor)
# - Sin operador ternario ? :
# - Corrige $. -> $_
# - Filtra valores no numéricos en ServerTime para cálculos
# - Espera arranque de servidor, ejecuta clientes, calcula métricas

$ErrorActionPreference = 'Stop'

# --- Configuración ---
$projectDir   = "C:\Users\jhonh\Downloads\helloworld-ciclo-kbd-AdriJhoannyMartinezMurillo1-JohanStivenGuzman2"
$serverJar    = "$projectDir\server\build\libs\server.jar"
$clientJar    = "$projectDir\client\build\libs\client.jar"
$iterations   = 5
$logDir       = "$projectDir\logs"

$clientLog1   = "$logDir\client1_log.csv"
$clientLog2   = "$logDir\client2_log.csv"
$serverLogCsv = "$logDir\server_log.csv"  # Debe coincidir con lo que escribe Server.java

# --- Utilidades ---
function Import-CsvIfExists {
    param([string]$Path)
    if (Test-Path $Path) {
        if ((Get-Item $Path).Length -eq 0) { return @() }
        try { return Import-Csv $Path } catch { return @() }
    } else { return @() }
}

# Regex para detectar números (enteros o decimales) con espacios opcionales
$NumRegex = '^\s*\d+(\.\d+)?\s*$'

# --- Preparación de carpeta de logs ---
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

# Borra SOLO CSV previos (mantén .txt de diagnóstico)
Get-ChildItem -Path $logDir -Filter *.csv -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue

# Permisos al usuario actual
try {
    icacls "$logDir" /grant "$($env:USERNAME):(OI)(CI)F" /T | Out-Null
} catch {
    Write-Warning "No se pudieron ajustar permisos con icacls (prueba como administrador si es necesario)."
}

# --- Compilación ---
Set-Location $projectDir
Write-Output "Compilando proyecto..."
& .\gradlew.bat clean
& .\gradlew.bat build

# Verificación de JARs
if (-not (Test-Path $serverJar) -or -not (Test-Path $clientJar)) {
    Write-Error "JARs no encontrados. Compilación fallida."
    exit 1
}

# --- Iniciar servidor ---
Write-Output "Iniciando servidor..."
$serverStartArgs = @{
    FilePath               = "java"
    ArgumentList           = @("-jar", $serverJar)
    WorkingDirectory       = $projectDir
    RedirectStandardOutput = "$logDir\server_output.txt"
    RedirectStandardError  = "$logDir\server_error.txt"
    PassThru               = $true
    NoNewWindow            = $true
}
$serverProcess = Start-Process @serverStartArgs

# Esperar a que el servidor arranque (hasta 20s). OJO: si crea CSV tras 1ª petición, puede no existir aún.
$maxWait = 20
for ($i = 0; $i -lt $maxWait; $i++) {
    if ($serverProcess.HasExited) {
        Write-Error "El servidor falló al iniciar. Revisa $logDir\server_error.txt"
        Get-Content "$logDir\server_error.txt" -ErrorAction SilentlyContinue
        exit 1
    }
    if (Test-Path $serverLogCsv) { break }
    Start-Sleep -Seconds 1
}

# --- Iniciar clientes en paralelo ---
Write-Output "Iniciando clientes..."
$client1Args = @{
    FilePath               = "java"
    ArgumentList           = @("-jar", $clientJar, "auto", $iterations, "client1")
    WorkingDirectory       = $projectDir
    RedirectStandardOutput = "$logDir\client1_output.txt"
    RedirectStandardError  = "$logDir\client1_error.txt"
    PassThru               = $true
    NoNewWindow            = $true
}
$client2Args = @{
    FilePath               = "java"
    ArgumentList           = @("-jar", $clientJar, "auto", $iterations, "client2")
    WorkingDirectory       = $projectDir
    RedirectStandardOutput = "$logDir\client2_output.txt"
    RedirectStandardError  = "$logDir\client2_error.txt"
    PassThru               = $true
    NoNewWindow            = $true
}
$clientProcess1 = Start-Process @client1Args
$clientProcess2 = Start-Process @client2Args

# Esperar con timeout
Write-Output "Esperando finalización de pruebas..."
$timeoutSec = 150
$startTime  = Get-Date
while (-not $clientProcess1.HasExited -or -not $clientProcess2.HasExited) {
    if (((Get-Date) - $startTime).TotalSeconds -gt $timeoutSec) {
        Write-Warning "Timeout alcanzado. Deteniendo clientes..."
        Stop-Process -Id $clientProcess1.Id -Force -ErrorAction SilentlyContinue
        Stop-Process -Id $clientProcess2.Id -Force -ErrorAction SilentlyContinue
        break
    }
    Start-Sleep -Seconds 2
}

# Detener servidor
Write-Output "Deteniendo servidor..."
if ($serverProcess -and -not $serverProcess.HasExited) {
    Stop-Process -Id $serverProcess.Id -Force -ErrorAction SilentlyContinue
}

# --- Procesar logs para métricas ---
Write-Output "Procesando resultados..."

# Cargar CSVs de cliente
$clientData1 = Import-CsvIfExists $clientLog1
$clientData2 = Import-CsvIfExists $clientLog2
$printerData = @($clientData1 + $clientData2) | Sort-Object { [long]$_.Timestamp }

if (-not $printerData -or $printerData.Count -lt 1) {
    Write-Warning "No hay datos en los CSV de clientes. Revisa client*_output/error.txt y asegúrate de que Client.java genere CSV (mín.: Timestamp,Command,ServerTime)."
    exit 0
}

# --- Throughput ---
$totalMessages = $printerData.Count
$firstTs       = [long]$printerData[0].Timestamp
$lastTs        = [long]$printerData[-1].Timestamp
$totalTimeSec  = [math]::Max(0, ($lastTs - $firstTs) / 1000)
$throughput    = if ($totalTimeSec -gt 0) { [math]::Round($totalMessages / $totalTimeSec, 3) } else { 0 }
Write-Output "Throughput: $throughput msg/seg"

# --- Response Time promedio por comando (filtrando no numéricos) ---
$responseTimes = $printerData | Group-Object Command | ForEach-Object {
    $cmd  = $_.Name
    $nums = $_.Group |
            Where-Object { $_.ServerTime -match $NumRegex } |
            ForEach-Object { [double]$_.ServerTime }
    $avg  = if ($nums.Count) { ($nums | Measure-Object -Average).Average } else { $null }
    "$cmd,$avg"
}
Write-Output "Response Times (ms):`n$responseTimes"

# --- Deadline Miss Rate ---
$deadlines = @{ "hello"=1000; "23"=1000; "listifs"=1000; "listports 127.0.0.1"=10000; "!ls"=10000 }
$deadlineMisses = $printerData |
        Where-Object {
            ($_.ServerTime -match $NumRegex) -and
                    ($deadlines.ContainsKey($_.Command)) -and
                    ([double]$_.ServerTime -gt [double]$deadlines[$_.Command])
        } | Measure-Object
$missRate = if ($totalTimeSec -gt 0) { [math]::Round($deadlineMisses.Count / $totalTimeSec, 6) } else { 0 }
Write-Output "Deadline Miss Rate: $missRate misses/seg"

# --- Jitter (desviación estándar por comando, filtrando no numéricos) ---
$jitters = $printerData | Group-Object Command | ForEach-Object {
    $cmd   = $_.Name
    $times = $_.Group |
            Where-Object { $_.ServerTime -match $NumRegex } |
            ForEach-Object { [double]$_.ServerTime }
    if ($times.Count -gt 1) {
        $mean     = ($times | Measure-Object -Average).Average
        $variance = ( $times | ForEach-Object { [math]::Pow($_ - $mean, 2) } | Measure-Object -Average ).Average
        $stdDev   = [math]::Sqrt($variance)
    } else { $stdDev = 0 }
    "$cmd,$stdDev"
}
Write-Output "Jitter (ms):`n$jitters"

# --- Missing Rate (en base a logs de cliente) ---
$sentMessages = ($clientData1.Count + $clientData2.Count)
$missingRate  = if ($totalTimeSec -gt 0) { [math]::Round(($sentMessages - $totalMessages) / $totalTimeSec, 6) } else { 0 }
Write-Output "Missing Rate: $missingRate msg/seg"

# --- Tiempos de Procesamiento (usa ServerTime como proxy si no hay FuncTime) ---
$funcTimes = $printerData | Group-Object Command | ForEach-Object {
    $cmd  = $_.Name
    $nums = $_.Group |
            Where-Object { $_.ServerTime -match $NumRegex } |
            ForEach-Object { [double]$_.ServerTime }
    $avg  = if ($nums.Count) { ($nums | Measure-Object -Average).Average } else { $null }
    "$cmd,$avg"
}
Write-Output "Func Times (ms):`n$funcTimes"

# --- Consultas / Reportes (primer uso vs posteriores) ---
$firstTimes = $printerData | Group-Object Command | ForEach-Object {
    $cmd   = $_.Name
    $rows  = $_.Group | Where-Object { $_.ServerTime -match $NumRegex }
    if ($rows.Count -ge 1) {
        $first    = [double]$rows[0].ServerTime
        $laterArr = @()
        if ($rows.Count -gt 1) {
            $laterArr = $rows | Select-Object -Skip 1 | ForEach-Object { [double]$_.ServerTime }
        }
        $avgLater = if ($laterArr.Count) { ($laterArr | Measure-Object -Average).Average } else { $first }
        "$cmd,First=$first,Later=$avgLater"
    } else {
        "$cmd,First=,Later="
    }
}
Write-Output "Consultas/Reportes (ms):`n$firstTimes"

Write-Output "Listo. Revisa archivos en: $logDir"

# (opcional) Muestra hasta 10 filas problemáticas donde ServerTime no sea numérico
# $basura = $printerData | Where-Object { $_.ServerTime -notmatch $NumRegex } | Select-Object -First 10
# if ($basura) { Write-Host "`nFilas no numéricas detectadas en ServerTime:"; $basura | Format-Table -Auto }

