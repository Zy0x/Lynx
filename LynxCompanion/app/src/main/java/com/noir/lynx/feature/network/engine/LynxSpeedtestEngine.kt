package com.noir.lynx.feature.network.engine

import com.noir.lynx.feature.network.model.SpeedtestPhase
import com.noir.lynx.feature.network.model.SpeedtestUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Native multi-stream speedtest & bufferbloat engine powered by open HTTP/TLS streams
 * (Cloudflare Speedtest Edge by default, with optional custom HTTPS LibreSpeed / file URLs).
 */
internal object LynxSpeedtestEngine {

    private const val CF_DOWN_URL = "https://speed.cloudflare.com/__down"
    private const val CF_UP_URL = "https://speed.cloudflare.com/__up"
    private const val USER_AGENT = "LynxCompanion/3.4.65"

    internal enum class CustomMode {
        CLOUDFLARE_FULL,
        CLOUDFLARE_LITE,
        CUSTOM_HTTP
    }

    internal data class ResolvedEndpoints(
        val mode: CustomMode,
        val pingUrl: String,
        val downloadUrl: String,
        val uploadUrl: String?,
        val serverLabel: String,
        val isLite: Boolean
    )

    internal fun sanitizeCustomUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        val withScheme = if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return withScheme.trimEnd('/')
    }

    internal fun parseCfRayEdge(cfRayHeader: String?): String {
        val raw = cfRayHeader?.trim().orEmpty()
        if (raw.isBlank()) return ""
        val dashIdx = raw.lastIndexOf('-')
        if (dashIdx >= 0 && dashIdx + 1 < raw.length) {
            val iata = raw.substring(dashIdx + 1).uppercase().filter { it.isLetter() }
            if (iata.length in 3..4) return "Cloudflare ($iata)"
        }
        return "Cloudflare Edge"
    }

    internal fun computeBufferbloatGrade(
        idleLatencyMs: Float,
        downloadLoadedMs: Float,
        uploadLoadedMs: Float
    ): Pair<String, String> {
        if (idleLatencyMs <= 0f) return "" to ""
        val peakLoaded = max(downloadLoadedMs, uploadLoadedMs)
        if (peakLoaded <= 0f) {
            return "—" to "Latensi di bawah beban tidak merespons penuh"
        }
        val delta = max(0f, peakLoaded - idleLatencyMs)
        val deltaInt = delta.roundToInt()
        return when {
            delta <= 15f -> "A+" to "Ping sangat stabil (+${deltaInt} ms saat jaringan sibuk)"
            delta <= 35f -> "A" to "Ping stabil untuk game online (+${deltaInt} ms saat jaringan sibuk)"
            delta <= 75f -> "B" to "Ada sedikit kenaikan ping (+${deltaInt} ms saat jaringan penuh)"
            delta <= 140f -> "C" to "Ping naik cukup tinggi (+${deltaInt} ms) — disarankan aktifkan Anti-Lag (fq_codel)"
            else -> "D" to "Ping melonjak tajam (+${deltaInt} ms) — aktifkan Mode Gaming atau pengatur antrean paket"
        }
    }

    private fun openConn(urlStr: String, method: String = "GET"): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 5500
        conn.readTimeout = 6500
        conn.useCaches = false
        conn.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
        conn.setRequestProperty("Pragma", "no-cache")
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept-Encoding", "identity")
        return conn
    }

    internal data class ProbeResult(
        val rttMs: Float,
        val edgeLabel: String?,
        val errorDetail: String? = null
    )

    internal fun classifyNetworkError(t: Throwable, serverLabel: String): String {
        val rawMsg = (t.message ?: t.cause?.message).orEmpty()
        val lower = rawMsg.lowercase()
        return when {
            t is SecurityException ||
                lower.contains("eperm") ||
                lower.contains("operation not permitted") ||
                lower.contains("eacces") ||
                lower.contains("permission denied") ->
                "Akses internet aplikasi Lynx diblokir oleh pengaturan sistem. Aktifkan 'Izinkan akses jaringan' di Setelan Aplikasi."

            t is UnknownHostException ->
                "Tidak dapat terhubung ke $serverLabel (DNS tidak merespons). Periksa koneksi internet, Private DNS, atau aktifkan izin jaringan Lynx di Setelan Aplikasi."

            t is SSLException ->
                "Koneksi aman (TLS/SSL) ke $serverLabel gagal. Periksa pengaturan tanggal & waktu perangkat."

            t is SocketTimeoutException ->
                "Waktu koneksi ke $serverLabel habis (sinyal lemah atau server sibuk)."

            t is ConnectException ->
                "Koneksi ditolak oleh $serverLabel."

            lower.contains("cleartext") ->
                "Server menggunakan http:// tanpa enkripsi. Gunakan alamat https://."

            else ->
                "Tidak dapat menjangkau $serverLabel. Periksa sambungan internet Anda."
        }
    }

    private fun probeSingleRttMs(urlStr: String, serverLabel: String = "server"): ProbeResult {
        var conn: HttpURLConnection? = null
        return try {
            val t0 = System.nanoTime()
            val httpConn = openConn("$urlStr${if ('?' in urlStr) '&' else '?'}t=$t0")
            conn = httpConn
            val code = httpConn.responseCode
            httpConn.inputStream?.use { ins ->
                val buf = ByteArray(1024)
                while (ins.read(buf) >= 0) {
                    // Drain lightweight ping response cleanly for socket reuse
                }
            }
            val elapsedMs = ((System.nanoTime() - t0) / 1_000_000.0).toFloat()
            val edge = parseCfRayEdge(httpConn.getHeaderField("cf-ray")).ifBlank { null }
            if (code in 200..399) {
                ProbeResult(elapsedMs, edge)
            } else {
                ProbeResult(-1f, null, "HTTP $code dari $serverLabel")
            }
        } catch (t: Throwable) {
            ProbeResult(-1f, null, classifyNetworkError(t, serverLabel))
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun resolveEndpoints(serverId: String, customUrlRaw: String): ResolvedEndpoints {
        return when (serverId) {
            "cloudflare_lite" -> ResolvedEndpoints(
                mode = CustomMode.CLOUDFLARE_LITE,
                pingUrl = "$CF_DOWN_URL?bytes=0",
                downloadUrl = "$CF_DOWN_URL?bytes=4000000",
                uploadUrl = CF_UP_URL,
                serverLabel = "Cloudflare Edge (Hemat Kuota)",
                isLite = true
            )
            "custom" -> {
                val clean = sanitizeCustomUrl(customUrlRaw)
                if (clean.isBlank()) {
                    throw IllegalArgumentException("URL server kustom belum diisi.")
                }
                val hostLabel = runCatching { URL(clean).host }.getOrNull().orEmpty().ifBlank { clean }
                if (clean.endsWith(".dat") || clean.endsWith(".bin") || clean.endsWith(".iso") || clean.endsWith(".zip")) {
                    ResolvedEndpoints(
                        mode = CustomMode.CUSTOM_HTTP,
                        pingUrl = clean,
                        downloadUrl = clean,
                        uploadUrl = null,
                        serverLabel = hostLabel,
                        isLite = false
                    )
                } else {
                    val emptyProbe = "$clean/backend/empty.php"
                    val altEmpty = "$clean/empty.php"
                    val hasBackend = probeSingleRttMs(emptyProbe, hostLabel).rttMs > 0f
                    val hasRootEmpty = !hasBackend && probeSingleRttMs(altEmpty, hostLabel).rttMs > 0f
                    val prefix = if (hasBackend) "$clean/backend" else clean
                    ResolvedEndpoints(
                        mode = CustomMode.CUSTOM_HTTP,
                        pingUrl = when {
                            hasBackend || hasRootEmpty -> "$prefix/empty.php"
                            else -> clean
                        },
                        downloadUrl = when {
                            hasBackend || hasRootEmpty -> "$prefix/garbage.php?ckSize=16"
                            else -> clean
                        },
                        uploadUrl = when {
                            hasBackend || hasRootEmpty -> "$prefix/empty.php"
                            else -> clean
                        },
                        serverLabel = hostLabel,
                        isLite = false
                    )
                }
            }
            else -> ResolvedEndpoints(
                mode = CustomMode.CLOUDFLARE_FULL,
                pingUrl = "$CF_DOWN_URL?bytes=0",
                downloadUrl = "$CF_DOWN_URL?bytes=20000000",
                uploadUrl = CF_UP_URL,
                serverLabel = "Cloudflare Edge",
                isLite = false
            )
        }
    }

    suspend fun runSpeedtest(
        initialState: SpeedtestUiState,
        onUpdate: (SpeedtestUiState) -> Unit
    ) = coroutineScope {
        var current = initialState.copy(
            phase = SpeedtestPhase.PING,
            progress = 0.04f,
            statusText = "Menghubungi server edge...",
            serverEdgeLabel = "",
            idleLatencyMs = 0f,
            idleJitterMs = 0f,
            downloadMbps = 0f,
            downloadPeakMbps = 0f,
            downloadLoadedLatencyMs = 0f,
            uploadMbps = 0f,
            uploadPeakMbps = 0f,
            uploadLoadedLatencyMs = 0f,
            totalTransferredMb = 0f,
            speedSamples = emptyList(),
            bufferbloatGrade = "",
            bufferbloatSummary = "",
            errorMessage = null
        )
        onUpdate(current)

        val resolved = try {
            resolveEndpoints(initialState.selectedServerId, initialState.customBaseUrl)
        } catch (t: Throwable) {
            onUpdate(
                current.copy(
                    phase = SpeedtestPhase.ERROR,
                    progress = 0f,
                    statusText = "Konfigurasi server tidak valid",
                    errorMessage = t.message ?: "URL server speedtest tidak valid"
                )
            )
            return@coroutineScope
        }

        // Phase 1: Warmup + Idle Ping & Jitter
        current = current.copy(
            statusText = "Mengukur Idle Ping & Jitter...",
            serverEdgeLabel = resolved.serverLabel,
            progress = 0.08f
        )
        onUpdate(current)

        val warmup = probeSingleRttMs(resolved.pingUrl, resolved.serverLabel)
        val detectedEdge = warmup.edgeLabel ?: resolved.serverLabel
        if (warmup.rttMs <= 0f) {
            onUpdate(
                current.copy(
                    phase = SpeedtestPhase.ERROR,
                    progress = 0f,
                    statusText = "Gagal menjangkau server uji",
                    errorMessage = warmup.errorDetail
                        ?: "Tidak ada respons HTTP/TLS dari ${resolved.serverLabel}. Periksa koneksi internet atau URL server."
                )
            )
            return@coroutineScope
        }

        val rttSamples = mutableListOf<Float>()
        for (i in 0 until 5) {
            if (!isActive) return@coroutineScope
            val probe = probeSingleRttMs(resolved.pingUrl, resolved.serverLabel)
            if (probe.rttMs > 0f) {
                rttSamples += probe.rttMs
            }
            val latestEdge = probe.edgeLabel ?: detectedEdge
            val sorted = rttSamples.sorted()
            val med = if (sorted.isNotEmpty()) sorted[sorted.size / 2] else 0f
            val jit = computeJitter(rttSamples)
            current = current.copy(
                serverEdgeLabel = latestEdge,
                idleLatencyMs = med,
                idleJitterMs = jit,
                progress = 0.08f + ((i + 1) / 5f) * 0.10f
            )
            onUpdate(current)
            delay(80)
        }

        if (rttSamples.isEmpty()) {
            onUpdate(
                current.copy(
                    phase = SpeedtestPhase.ERROR,
                    progress = 0f,
                    statusText = "Server tidak merespons uji ping",
                    errorMessage = "Gagal memperoleh sampel RTT yang valid."
                )
            )
            return@coroutineScope
        }

        val finalIdlePing = current.idleLatencyMs
        val finalIdleJitter = current.idleJitterMs

        // Phase 2: Multi-stream Download + Concurrent Loaded Latency
        val dlDurationMs = if (resolved.isLite) 3200L else 5500L
        val dlWorkersCount = if (resolved.isLite) 3 else 4
        val dlWarmupMs = 450L

        val dlTotalBytes = AtomicLong(0L)
        val dlSteadyBytes = AtomicLong(0L)
        val dlActive = AtomicBoolean(true)
        val dlConns = CopyOnWriteArrayList<HttpURLConnection>()
        val dlLoadedSamples = CopyOnWriteArrayList<Float>()
        val dlSpeedHistory = mutableListOf<Float>()

        val dlStartNs = System.nanoTime()
        current = current.copy(
            phase = SpeedtestPhase.DOWNLOAD,
            statusText = "Menguji Download ($dlWorkersCount stream) + Loaded Latency...",
            speedSamples = emptyList()
        )
        onUpdate(current)

        coroutineScope {
            val workerJobs = List(dlWorkersCount) { workerIdx ->
                launch(Dispatchers.IO) {
                    val buf = ByteArray(32_768)
                    while (dlActive.get() && isActive) {
                        var conn: HttpURLConnection? = null
                        try {
                            val reqUrl = if (resolved.mode == CustomMode.CUSTOM_HTTP) {
                                resolved.downloadUrl
                            } else {
                                // Start with medium chunk on first wave or larger chunk for sustained bandwidth
                                val sizeBytes = if (resolved.isLite) 4_000_000 else 20_000_000
                                "$CF_DOWN_URL?bytes=$sizeBytes&r=${System.nanoTime()}_$workerIdx"
                            }
                            conn = openConn(reqUrl)
                            dlConns.add(conn)
                            val code = conn.responseCode
                            if (code !in 200..399) {
                                break
                            }
                            conn.inputStream.use { ins ->
                                while (dlActive.get() && isActive) {
                                    val read = ins.read(buf)
                                    if (read <= 0) break
                                    dlTotalBytes.addAndGet(read.toLong())
                                    val elapsedMs = (System.nanoTime() - dlStartNs) / 1_000_000L
                                    if (elapsedMs >= dlWarmupMs) {
                                        dlSteadyBytes.addAndGet(read.toLong())
                                    }
                                }
                            }
                        } catch (_: Exception) {
                            if (!dlActive.get()) break
                            delay(80)
                        } finally {
                            conn?.let {
                                dlConns.remove(it)
                                runCatching { it.disconnect() }
                            }
                        }
                    }
                }
            }

            val pingLoadJob = launch(Dispatchers.IO) {
                delay(400)
                while (dlActive.get() && isActive) {
                    val probe = probeSingleRttMs(resolved.pingUrl, resolved.serverLabel)
                    if (probe.rttMs > 0f) {
                        dlLoadedSamples.add(probe.rttMs)
                    }
                    delay(420)
                }
            }

            var prevBytes = 0L
            var prevTimeNs = dlStartNs
            var peakDlMbps = 0f

            while (isActive) {
                val elapsedMs = (System.nanoTime() - dlStartNs) / 1_000_000L
                if (elapsedMs >= dlDurationMs) break
                delay(220)

                val nowNs = System.nanoTime()
                val curTotal = dlTotalBytes.get()
                val deltaBytes = max(0L, curTotal - prevBytes)
                val deltaSec = max(0.05, (nowNs - prevTimeNs) / 1_000_000_000.0)
                val instMbps = ((deltaBytes * 8.0) / (deltaSec * 1_000_000.0)).toFloat()
                prevBytes = curTotal
                prevTimeNs = nowNs

                val steadyElapsedMs = max(1L, ((nowNs - dlStartNs) / 1_000_000L) - dlWarmupMs)
                val steadyBytes = dlSteadyBytes.get()
                val avgDlMbps = if (steadyBytes > 0L && steadyElapsedMs > 120L) {
                    ((steadyBytes * 8.0) / ((steadyElapsedMs / 1000.0) * 1_000_000.0)).toFloat()
                } else {
                    val allSec = max(0.2, (nowNs - dlStartNs) / 1_000_000_000.0)
                    ((curTotal * 8.0) / (allSec * 1_000_000.0)).toFloat()
                }
                if (elapsedMs > dlWarmupMs && instMbps > peakDlMbps) {
                    peakDlMbps = instMbps
                }
                dlSpeedHistory.add(instMbps)
                val loadedMedian = medianFloat(dlLoadedSamples)
                val frac = (elapsedMs.toFloat() / dlDurationMs.toFloat()).coerceIn(0f, 1f)
                current = current.copy(
                    downloadMbps = avgDlMbps,
                    downloadPeakMbps = max(peakDlMbps, avgDlMbps),
                    downloadLoadedLatencyMs = loadedMedian,
                    totalTransferredMb = curTotal / (1024f * 1024f),
                    speedSamples = dlSpeedHistory.takeLast(28),
                    progress = 0.18f + frac * 0.40f
                )
                onUpdate(current)
            }

            dlActive.set(false)
            dlConns.forEach { c -> runCatching { c.disconnect() } }
            workerJobs.forEach { it.cancel() }
            pingLoadJob.cancel()
        }

        val finalDlSteadyMs = max(300L, ((System.nanoTime() - dlStartNs) / 1_000_000L) - dlWarmupMs)
        val finalDlSteadyBytes = dlSteadyBytes.get()
        val finalDlMbps = if (finalDlSteadyBytes > 0L) {
            ((finalDlSteadyBytes * 8.0) / ((finalDlSteadyMs / 1000.0) * 1_000_000.0)).toFloat()
        } else {
            current.downloadMbps
        }
        val finalDlLoaded = medianFloat(dlLoadedSamples).takeIf { it > 0f } ?: finalIdlePing

        // Phase 3: Adaptive Multi-Stream Upload + Concurrent Loaded Latency
        val ulBytesStartTotal = dlTotalBytes.get()
        var finalUlMbps = 0f
        var finalUlPeak = 0f
        var finalUlLoaded = finalIdlePing
        var uploadRejected = false
        val ulTotalBytes = AtomicLong(0L)

        if (resolved.uploadUrl != null && isActive) {
            val ulDurationMs = if (resolved.isLite) 3000L else 5000L
            val ulWarmupMs = 450L
            val (ulWorkersCount, ulChunkBytes) = when {
                resolved.isLite -> 2 to 524_288
                finalDlMbps < 12f -> 2 to 524_288
                finalDlMbps < 60f -> 3 to 1_572_864
                else -> 4 to 3_145_728
            }

            val ulSteadyBytes = AtomicLong(0L)
            val ulActive = AtomicBoolean(true)
            val ulConns = CopyOnWriteArrayList<HttpURLConnection>()
            val ulLoadedSamples = CopyOnWriteArrayList<Float>()
            val ulSpeedHistory = mutableListOf<Float>()
            val uploadWriteFailure = AtomicBoolean(false)

            val ulStartNs = System.nanoTime()
            current = current.copy(
                phase = SpeedtestPhase.UPLOAD,
                downloadMbps = finalDlMbps,
                downloadLoadedLatencyMs = finalDlLoaded,
                statusText = "Menguji Upload ($ulWorkersCount stream) + Loaded Latency...",
                speedSamples = emptyList()
            )
            onUpdate(current)

            coroutineScope {
                val workerJobs = List(ulWorkersCount) {
                    launch(Dispatchers.IO) {
                        val slice = ByteArray(32_768) { idx -> (idx and 0x7F).toByte() }
                        while (ulActive.get() && isActive) {
                            var conn: HttpURLConnection? = null
                            try {
                                val targetUrl = resolved.uploadUrl +
                                    "${if ('?' in resolved.uploadUrl) '&' else '?'}r=${System.nanoTime()}"
                                val httpConn = openConn(targetUrl, method = "POST")
                                conn = httpConn
                                httpConn.doOutput = true
                                httpConn.setFixedLengthStreamingMode(ulChunkBytes)
                                httpConn.setRequestProperty("Content-Type", "application/octet-stream")
                                ulConns.add(httpConn)

                                httpConn.outputStream.use { os ->
                                    var remaining = ulChunkBytes
                                    while (remaining > 0 && ulActive.get() && isActive) {
                                        val toWrite = minOf(remaining, slice.size)
                                        os.write(slice, 0, toWrite)
                                        remaining -= toWrite
                                        ulTotalBytes.addAndGet(toWrite.toLong())
                                        val elapsedMs = (System.nanoTime() - ulStartNs) / 1_000_000L
                                        if (elapsedMs >= ulWarmupMs) {
                                            ulSteadyBytes.addAndGet(toWrite.toLong())
                                        }
                                    }
                                    os.flush()
                                }
                                val code = httpConn.responseCode
                                if (code in 400..599 && ulTotalBytes.get() <= ulChunkBytes) {
                                    uploadWriteFailure.set(true)
                                    break
                                }
                                runCatching {
                                    httpConn.inputStream?.use { ins ->
                                        val drain = ByteArray(1024)
                                        while (ins.read(drain) >= 0) {
                                            // Drain ACK cleanly
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                                if (!ulActive.get()) break
                                delay(80)
                            } finally {
                                conn?.let {
                                    ulConns.remove(it)
                                    runCatching { it.disconnect() }
                                }
                            }
                        }
                    }
                }

                val pingLoadJob = launch(Dispatchers.IO) {
                    delay(400)
                    while (ulActive.get() && isActive) {
                        val probe = probeSingleRttMs(resolved.pingUrl, resolved.serverLabel)
                        if (probe.rttMs > 0f) {
                            ulLoadedSamples.add(probe.rttMs)
                        }
                        delay(420)
                    }
                }

                var prevBytes = 0L
                var prevTimeNs = ulStartNs
                while (isActive) {
                    val elapsedMs = (System.nanoTime() - ulStartNs) / 1_000_000L
                    if (elapsedMs >= ulDurationMs) break
                    delay(220)

                    val nowNs = System.nanoTime()
                    val curTotal = ulTotalBytes.get()
                    val deltaBytes = max(0L, curTotal - prevBytes)
                    val deltaSec = max(0.05, (nowNs - prevTimeNs) / 1_000_000_000.0)
                    val instMbps = ((deltaBytes * 8.0) / (deltaSec * 1_000_000.0)).toFloat()
                    prevBytes = curTotal
                    prevTimeNs = nowNs

                    val steadyElapsedMs = max(1L, ((nowNs - ulStartNs) / 1_000_000L) - ulWarmupMs)
                    val steadyBytes = ulSteadyBytes.get()
                    val avgUlMbps = if (steadyBytes > 0L && steadyElapsedMs > 120L) {
                        ((steadyBytes * 8.0) / ((steadyElapsedMs / 1000.0) * 1_000_000.0)).toFloat()
                    } else {
                        val allSec = max(0.2, (nowNs - ulStartNs) / 1_000_000_000.0)
                        ((curTotal * 8.0) / (allSec * 1_000_000.0)).toFloat()
                    }
                    if (elapsedMs > ulWarmupMs && instMbps > finalUlPeak) {
                        finalUlPeak = instMbps
                    }
                    ulSpeedHistory.add(instMbps)
                    val loadedMedian = medianFloat(ulLoadedSamples)
                    val frac = (elapsedMs.toFloat() / ulDurationMs.toFloat()).coerceIn(0f, 1f)
                    current = current.copy(
                        uploadMbps = avgUlMbps,
                        uploadPeakMbps = max(finalUlPeak, avgUlMbps),
                        uploadLoadedLatencyMs = loadedMedian,
                        totalTransferredMb = (ulBytesStartTotal + curTotal) / (1024f * 1024f),
                        speedSamples = ulSpeedHistory.takeLast(28),
                        progress = 0.60f + frac * 0.38f
                    )
                    onUpdate(current)
                }

                ulActive.set(false)
                ulConns.forEach { c -> runCatching { c.disconnect() } }
                workerJobs.forEach { it.cancel() }
                pingLoadJob.cancel()
            }

            val finalUlSteadyMs = max(300L, ((System.nanoTime() - ulStartNs) / 1_000_000L) - ulWarmupMs)
            val finalUlSteadyBytes = ulSteadyBytes.get()
            finalUlMbps = if (uploadWriteFailure.get()) {
                0f
            } else if (finalUlSteadyBytes > 0L) {
                ((finalUlSteadyBytes * 8.0) / ((finalUlSteadyMs / 1000.0) * 1_000_000.0)).toFloat()
            } else {
                current.uploadMbps
            }
            uploadRejected = uploadWriteFailure.get()
            finalUlLoaded = medianFloat(ulLoadedSamples).takeIf { it > 0f } ?: finalIdlePing
        }

        val (grade, summary) = computeBufferbloatGrade(
            idleLatencyMs = finalIdlePing,
            downloadLoadedMs = finalDlLoaded,
            uploadLoadedMs = finalUlLoaded
        )

        onUpdate(
            current.copy(
                phase = SpeedtestPhase.COMPLETED,
                progress = 1f,
                statusText = when {
                    resolved.uploadUrl == null -> "Uji Download selesai (endpoint hanya Download)"
                    uploadRejected -> "Download selesai (server kustom menolak Upload POST)"
                    else -> "Uji Speedtest selesai"
                },
                idleLatencyMs = finalIdlePing,
                idleJitterMs = finalIdleJitter,
                downloadMbps = finalDlMbps,
                downloadPeakMbps = max(current.downloadPeakMbps, finalDlMbps),
                downloadLoadedLatencyMs = finalDlLoaded,
                uploadMbps = finalUlMbps,
                uploadPeakMbps = max(current.uploadPeakMbps, finalUlMbps),
                uploadLoadedLatencyMs = finalUlLoaded,
                totalTransferredMb = (ulBytesStartTotal + ulTotalBytes.get()) / (1024f * 1024f),
                bufferbloatGrade = grade,
                bufferbloatSummary = summary,
                lastTestedAtMs = System.currentTimeMillis()
            )
        )
    }

    internal fun computeJitter(samples: List<Float>): Float {
        if (samples.size < 2) return 0f
        var diffSum = 0f
        for (i in 1 until samples.size) {
            diffSum += abs(samples[i] - samples[i - 1])
        }
        return diffSum / (samples.size - 1)
    }

    private fun medianFloat(samples: Collection<Float>): Float {
        if (samples.isEmpty()) return 0f
        val sorted = samples.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2f
        }
    }
}
