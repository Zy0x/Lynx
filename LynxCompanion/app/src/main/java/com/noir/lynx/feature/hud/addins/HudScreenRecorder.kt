package com.noir.lynx.feature.hud.addins

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaCodecList
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import java.io.File
import java.nio.ByteBuffer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.view.WindowManager
import androidx.annotation.Keep
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private fun isMediaTekDevice(): Boolean = runCatching {
    val hardware = Build.HARDWARE.lowercase()
    val board = Build.BOARD.lowercase()
    val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER.lowercase() else ""
    hardware.startsWith("mt") || board.startsWith("mt") || soc.contains("mediatek") ||
            java.io.File("/proc/ged").exists() || java.io.File("/proc/ppm").exists()
}.getOrDefault(false)

internal fun recorderSize(width: Int, height: Int, quality: Int): Pair<Int, Int> {
    require(width > 0 && height > 0)
    val cap = when (quality) { 0 -> 1280; 1 -> 1920; else -> maxOf(width, height) }
    val scale = minOf(1.0, cap.toDouble() / maxOf(width, height))
    return maxOf(16, ((width * scale).roundToInt() / 16) * 16) to maxOf(16, ((height * scale).roundToInt() / 16) * 16)
}

@Keep enum class HudRecordingPhase { CHECKING, IDLE, COUNTDOWN, RECORDING, SAVING, SAVED, FAILED, UNSUPPORTED }
@Keep data class HudRecordingState(
    val phase: HudRecordingPhase = HudRecordingPhase.CHECKING,
    val countdown: Int = 0,
    val startedAt: Long = 0L,
    val segments: Int = 0,
    val lastUri: String? = null,
    val software: Boolean = false
) {
    val busy get() = phase in listOf(HudRecordingPhase.COUNTDOWN, HudRecordingPhase.RECORDING, HudRecordingPhase.SAVING)
}

/** Root AOSP recorder. MP4 segments remain independent; no audio or protected-content bypass. */
@Keep
class HudScreenRecorder(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("hud_recorder", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(HudRecordingState())
    val state = mutable.asStateFlow()
    private val mutableQuality = MutableStateFlow(prefs.getInt("quality", 1).coerceIn(0, 2))
    val quality = mutableQuality.asStateFlow()
    private val isMtk = isMediaTekDevice()
    private val mutableCompatibility = MutableStateFlow(prefs.getBoolean("compatibility", isMtk))
    val compatibility = mutableCompatibility.asStateFlow()
    private val mutableAudioSource = MutableStateFlow(prefs.getInt("audio_source", 0).coerceIn(0, 3))
    val audioSource = mutableAudioSource.asStateFlow()
    private val mutableFps = MutableStateFlow(prefs.getInt("fps_target", 60).coerceIn(30, 120))
    val fps = mutableFps.asStateFlow()
    private val mutableBitrate = MutableStateFlow(prefs.getInt("bitrate_idx", 1).coerceIn(0, 3))
    val bitrate = mutableBitrate.asStateFlow()
    private val mutableStealthOverlay = MutableStateFlow(prefs.getBoolean("stealth_overlay", true))
    val stealthOverlay = mutableStealthOverlay.asStateFlow()
    private var softwareCodec: String? = null
    @Volatile private var stopRequested = false
    @Volatile private var disposed = false
    @Volatile private var active: Output? = null
    @Volatile private var pid = 0
    private var interruptedPid = 0
    private var job: Job? = null

    private data class Output(val path: String, val name: String, val uri: Uri?, val descriptor: ParcelFileDescriptor? = null)
    init { scope.launch { runCatching { recover() }; probe() } }
    fun getStatus(): HudRecordingState = mutable.value
    fun outputSize(): Pair<Int, Int>? = runCatching {
        val dimensions = dimensions()
        recorderSize(dimensions.first, dimensions.second, if (compatibility.value || (state.value.busy && state.value.software)) 0 else quality.value)
    }.getOrNull()

    private fun resolveSoftwareCodec(): String? {
        if (softwareCodec != null) return softwareCodec
        softwareCodec = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { info ->
                info.isEncoder && info.isSoftwareOnly && info.supportedTypes.any { it.equals("video/avc", true) }
            }?.name?.takeIf { it.matches(Regex("[A-Za-z0-9._-]+")) }
                ?: MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { info ->
                    info.isEncoder && (info.name.contains("google", ignoreCase = true) || info.name.contains("c2.android", ignoreCase = true)) &&
                            info.supportedTypes.any { it.equals("video/avc", true) }
                }?.name?.takeIf { it.matches(Regex("[A-Za-z0-9._-]+")) }
        }.getOrNull() ?: "c2.android.avc.encoder"
        return softwareCodec
    }

    private fun probe() {
        val available = Build.VERSION.SDK_INT >= 29 && runCatching {
            Shell.getShell().isRoot && Shell.cmd("test -x /system/bin/screenrecord").exec().isSuccess
        }.getOrDefault(false)
        if (Build.VERSION.SDK_INT >= 29) {
            resolveSoftwareCodec()
        }
        mutable.value = mutable.value.copy(phase = if (available) HudRecordingPhase.IDLE else HudRecordingPhase.UNSUPPORTED,
            lastUri = prefs.getString("last_saved", null))
    }

    fun setQuality(value: Int) {
        if (state.value.busy) return
        mutableQuality.value = value.coerceIn(0, 2)
        prefs.edit().putInt("quality", mutableQuality.value).apply()
    }
    fun setCompatibility(enabled: Boolean) {
        if (state.value.busy) return
        mutableCompatibility.value = enabled
        prefs.edit().putBoolean("compatibility", enabled).apply()
    }
    fun setAudioSource(source: Int) {
        if (state.value.busy) return
        mutableAudioSource.value = source.coerceIn(0, 3)
        prefs.edit().putInt("audio_source", mutableAudioSource.value).apply()
    }
    fun setFps(targetFps: Int) {
        if (state.value.busy) return
        mutableFps.value = targetFps.coerceIn(30, 120)
        prefs.edit().putInt("fps_target", mutableFps.value).apply()
    }
    fun setBitrate(index: Int) {
        if (state.value.busy) return
        mutableBitrate.value = index.coerceIn(0, 3)
        prefs.edit().putInt("bitrate_idx", mutableBitrate.value).apply()
    }
    fun setStealthOverlay(enabled: Boolean) {
        mutableStealthOverlay.value = enabled
        prefs.edit().putBoolean("stealth_overlay", enabled).apply()
    }

    fun start() = launchRecording(null, 180, true)

    /** Signed instrumentation can exercise the same process lifecycle without writing outside Debug. */
    fun startValidation(path: String, seconds: Int) {
        require(path.matches(Regex("/storage/emulated/0/Debug/LynxRecorderValidation_[A-Za-z0-9_]+\\.mp4")))
        launchRecording(path, seconds.coerceIn(2, 15), false)
    }

    @Synchronized private fun launchRecording(validationPath: String?, seconds: Int, continuous: Boolean) {
        if (Build.VERSION.SDK_INT < 29) return
        if (disposed || job?.isActive == true || state.value.busy || state.value.phase == HudRecordingPhase.UNSUPPORTED || state.value.phase == HudRecordingPhase.CHECKING) return
        stopRequested = false
        interruptedPid = 0
        mutable.value = HudRecordingState(HudRecordingPhase.COUNTDOWN, countdown = if (validationPath == null) 3 else 0)
        job = scope.launch {
            var completed = 0
            var lastUri: String? = prefs.getString("last_saved", null)
            var software = compatibility.value
            try {
                if (validationPath == null) for (n in 3 downTo 1) {
                    mutable.value = mutable.value.copy(countdown = n)
                    delay(1000)
                    if (stopRequested) break
                }
                if (stopRequested) { mutable.value = mutable.value.copy(phase = HudRecordingPhase.IDLE); return@launch }
                var started = 0L
                while (!stopRequested && !disposed) {
                    if (stopRequested) break
                    val output = if (validationPath == null) createOutput() else Output(validationPath, validationPath.substringAfterLast('/'), null)
                    active = output
                    val (w, h) = dimensions()
                    val size = recorderSize(w, h, if (software) 0 else quality.value)
                    val bitrateBps = if (software) 2_000_000 else when (bitrate.value) { 0 -> 4_000_000; 1 -> 8_000_000; 2 -> 16_000_000; else -> 24_000_000 }
                    check(prefs.edit().putString("active_uri", output.uri?.toString()).putString("active_path", output.path)
                        .putString("active_name", output.name).commit())
                    val resolvedCodec = if (software) resolveSoftwareCodec() ?: "c2.android.avc.encoder" else null
                    val codec = if (resolvedCodec != null) "--codec-name $resolvedCodec" else ""
                    val appPid = android.os.Process.myPid()
                    val processName = android.app.Application.getProcessName().takeIf { it.matches(Regex("[A-Za-z0-9_.:]+")) } ?: error("invalid process identity")
                    val command = """
                        mkdir -p /storage/emulated/0/Movies/Lynx 2>/dev/null
                        /system/bin/screenrecord $codec --size ${size.first}x${size.second} --bit-rate $bitrateBps --time-limit $seconds '${output.path}' &
                        rec=${'$'}!
                        echo LYNX_REC_PID=${'$'}rec
                        echo ${'$'}rec > /data/local/tmp/lynx_rec.pid 2>/dev/null
                        (
                            while [ -d /proc/${'$'}rec ]; do
                                parent=${'$'}(tr '\000' ' ' < /proc/$appPid/cmdline 2>/dev/null)
                                case "${'$'}parent" in "$processName"*) ;; *)
                                    own=0
                                    for fd in /proc/${'$'}rec/fd/*; do
                                        case "${'$'}(readlink "${'$'}fd" 2>/dev/null)" in */${output.name}) own=1;; esac
                                    done
                                    [ "${'$'}own" = 1 ] && kill -INT "${'$'}rec"
                                    break;;
                                esac
                                sleep 1
                            done
                        ) &
                        watcher=${'$'}!
                        wait "${'$'}rec"
                        result=${'$'}?
                        rm -f /data/local/tmp/lynx_rec.pid 2>/dev/null
                        owner=${'$'}(awk '/^PPid:/ {print ${'$'}2}' /proc/${'$'}watcher/status 2>/dev/null)
                        [ "${'$'}owner" = "${'$'}${'$'}" ] && kill "${'$'}watcher" 2>/dev/null
                        wait "${'$'}watcher" 2>/dev/null
                        exit "${'$'}result"
                    """.trimIndent()
                    val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
                    val audioSrc = audioSource.value
                    var tempAudioFile: File? = null
                    var mediaRecorder: MediaRecorder? = null
                    var successful = false
                    try {
                        val reader = process.inputStream.bufferedReader()
                        // The root wrapper reports its child PID; never signal an unverified recorder.
                        val deadline = SystemClock.elapsedRealtime() + 4000
                        while (pid <= 1 && SystemClock.elapsedRealtime() < deadline && process.isAlive) {
                            if (reader.ready() || process.inputStream.available() > 0) {
                                val header = reader.readLine() ?: break
                                val parsed = Regex("LYNX_REC_PID=([0-9]+)").find(header)?.groupValues?.get(1)?.toIntOrNull()
                                if (parsed != null && parsed > 1) {
                                    pid = parsed
                                    break
                                }
                            }
                            if (pid <= 1) {
                                val filePid = runCatching { java.io.File("/data/local/tmp/lynx_rec.pid").readText().trim().toIntOrNull() }.getOrNull()
                                if (filePid != null && filePid > 1) {
                                    pid = filePid
                                    break
                                }
                            }
                            delay(40)
                        }
                        check(pid > 1)
                        prefs.edit().putInt("active_pid", pid).commit()
                        delay(200)
                        check(process.isAlive)
                        if (started == 0L) started = SystemClock.elapsedRealtime()
                        mutable.value = HudRecordingState(HudRecordingPhase.RECORDING, startedAt = started, segments = completed, lastUri = lastUri, software = software)

                        // Start optional parallel audio capture (Mic / Internal)
                        if (audioSrc > 0) {
                            runCatching {
                                Shell.cmd("pm grant ${context.packageName} android.permission.RECORD_AUDIO").exec()
                                val af = File(context.cacheDir, "lynx_audio_${System.currentTimeMillis()}.m4a")
                                val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
                                val sourceConstant = when (audioSrc) {
                                    1 -> MediaRecorder.AudioSource.MIC
                                    2 -> if (Build.VERSION.SDK_INT >= 29) MediaRecorder.AudioSource.VOICE_PERFORMANCE else MediaRecorder.AudioSource.MIC
                                    else -> MediaRecorder.AudioSource.MIC
                                }
                                mr.setAudioSource(sourceConstant)
                                mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                mr.setAudioEncodingBitRate(192_000)
                                mr.setAudioSamplingRate(48_000)
                                mr.setOutputFile(af.absolutePath)
                                mr.prepare()
                                mr.start()
                                tempAudioFile = af
                                mediaRecorder = mr
                            }
                        }

                        if (stopRequested) signal("INT", pid, output)
                        val exited = runInterruptible { process.waitFor(seconds.toLong() + 8, TimeUnit.SECONDS) }
                        if (!exited) {
                            signal("INT", pid, output)
                            runInterruptible { process.waitFor(5, TimeUnit.SECONDS) }
                        }
                        if (process.isAlive) signal("KILL", pid, output)
                        successful = !process.isAlive && process.exitValue() == 0
                    } catch (_: Exception) { successful = false }
                    finally {
                        mutable.value = mutable.value.copy(phase = HudRecordingPhase.SAVING)
                        mediaRecorder?.let { mr ->
                            runCatching { mr.stop() }
                            runCatching { mr.release() }
                        }
                        mediaRecorder = null
                        if (process.isAlive) signal("INT", pid, output)
                        if (process.isAlive) runInterruptible { process.waitFor(3, TimeUnit.SECONDS) }
                        if (process.isAlive) signal("KILL", pid, output)
                        output.descriptor?.close()
                        process.destroy()
                        pid = 0
                        val audioToMux = tempAudioFile
                        if (successful && audioToMux != null && audioToMux.exists() && audioToMux.length() > 512L) {
                            runCatching {
                                muxAudioIntoVideo(output.path, audioToMux.absolutePath)
                            }
                        }
                        audioToMux?.delete()
                    }
                    val valid = finalizeOutput(output, successful)
                    active = null
                    clearActive()
                    if (!valid && !software && softwareCodec != null && !stopRequested) {
                        software = true
                        if (completed == 0) started = 0L
                        continue
                    }
                    check(valid)
                    completed++
                    lastUri = output.uri?.toString()
                    if (lastUri != null) prefs.edit().putString("last_saved", lastUri).apply()
                    if (software) { mutableCompatibility.value = true; prefs.edit().putBoolean("compatibility", true).apply() }
                    mutable.value = HudRecordingState(
                        if (continuous && !stopRequested && !disposed) HudRecordingPhase.SAVING else HudRecordingPhase.SAVED,
                        startedAt = started, segments = completed, lastUri = lastUri, software = software)
                    if (!continuous) break
                }
                mutable.value = mutable.value.copy(phase = if (completed > 0) HudRecordingPhase.SAVED else HudRecordingPhase.IDLE)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                active?.let { output ->
                    signal("INT", pid, output)
                    runCatching { output.descriptor?.close() }
                    output.uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
                }
                active = null; pid = 0; clearActive()
                mutable.value = mutable.value.copy(phase = HudRecordingPhase.FAILED, segments = completed, lastUri = lastUri)
            } finally { if (disposed) scope.cancel() }
        }
    }

    private fun dimensions(): Pair<Int, Int> {
        val windows = context.getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) windows.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        else android.util.DisplayMetrics().also { windows.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
    }

    private fun createOutput(): Output {
        val name = "Lynx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.mp4"
        val dir = java.io.File("/storage/emulated/0/Movies/Lynx")
        if (!dir.exists()) dir.mkdirs()
        val directFile = java.io.File(dir, name)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Lynx")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = runCatching { context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) }.getOrNull()
        val descriptor = uri?.let { runCatching { context.contentResolver.openFileDescriptor(it, "rw") }.getOrNull() }
        val path = directFile.absolutePath
        return Output(path, name, uri, descriptor)
    }

    private fun finalizeOutput(output: Output, completed: Boolean): Boolean {
        val file = java.io.File(output.path)
        val hasFile = completed && file.exists() && file.length() > 1024L
        if (output.uri == null) return hasFile

        var valid = false
        if (hasFile) {
            valid = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(output.path)
                    (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0) > 0 &&
                        (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0) > 0
                } finally { retriever.release() }
            }.getOrDefault(false)
        }

        if (valid) {
            runCatching {
                context.contentResolver.update(output.uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            }
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(output.path),
                arrayOf("video/mp4")
            ) { _, scannedUri ->
                if (scannedUri != null) {
                    prefs.edit().putString("last_saved", scannedUri.toString()).apply()
                }
            }
            return true
        } else {
            runCatching { file.delete() }
            runCatching { context.contentResolver.delete(output.uri, null, null) }
            return false
        }
    }

    @Synchronized private fun signal(signal: String, requestedPid: Int, output: Output) {
        if (signal !in listOf("INT", "KILL")) return
        // A unique file name as well as the command and output path must match before signaling.
        val safeName = output.name.takeIf { it.matches(Regex("[A-Za-z0-9_.-]+")) } ?: return
        val safePath = output.path.takeIf { it.matches(Regex("/proc/[0-9]+/fd/[0-9]+|/storage/emulated/0/(Debug|Movies/Lynx)/[A-Za-z0-9_.-]+\\.mp4|/sdcard/Movies/Lynx/[A-Za-z0-9_.-]+\\.mp4")) } ?: return
        val targetPid = if (requestedPid > 1) requestedPid else Shell.cmd("""
            for p in ${'$'}(pidof screenrecord); do
                cmd=${'$'}(tr '\000' ' ' < /proc/${'$'}p/cmdline 2>/dev/null)
                case "${'$'}cmd" in *screenrecord*"$safePath"*) ;; *) continue;; esac
                for fd in /proc/${'$'}p/fd/*; do
                    case "${'$'}(readlink "${'$'}fd" 2>/dev/null)" in */$safeName) echo "${'$'}p"; exit 0;; esac
                done
            done
        """.trimIndent()).exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: return
        if (signal == "INT" && interruptedPid == targetPid) return
        val result = Shell.cmd("""
            cmd=${'$'}(tr '\000' ' ' < /proc/$targetPid/cmdline 2>/dev/null)
            case "${'$'}cmd" in *screenrecord*"$safePath"*) ;; *) exit 1;; esac
            own=0
            for fd in /proc/$targetPid/fd/*; do
                case "${'$'}(readlink "${'$'}fd" 2>/dev/null)" in */$safeName) own=1;; esac
            done
            [ "${'$'}own" = 1 ] && kill -$signal $targetPid
        """.trimIndent()).exec()
        if (signal == "INT" && result.isSuccess) interruptedPid = targetPid
    }

    fun stop() {
        stopRequested = true
        active?.let { output -> scope.launch { signal("INT", pid, output) } }
    }
    fun close() {
        disposed = true; stop()
        if (job?.isActive != true) scope.cancel()
    }
    fun openLast() {
        state.value.lastUri?.let { uri -> runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "video/mp4")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } }
    }
    private fun muxAudioIntoVideo(videoPath: String, audioPath: String): Boolean {
        val videoFile = File(videoPath)
        val audioFile = File(audioPath)
        if (!videoFile.exists() || !audioFile.exists() || audioFile.length() < 512L) return false

        val tempMuxPath = "$videoPath.mux.mp4"
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        return try {
            videoExtractor.setDataSource(videoPath)
            audioExtractor.setDataSource(audioPath)

            var videoTrackIndex = -1
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    break
                }
            }
            var audioTrackIndex = -1
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }
            if (videoTrackIndex < 0 || audioTrackIndex < 0) return false

            muxer = MediaMuxer(tempMuxPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxVideoTrack = muxer.addTrack(videoExtractor.getTrackFormat(videoTrackIndex))
            val muxAudioTrack = muxer.addTrack(audioExtractor.getTrackFormat(audioTrackIndex))
            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()

            videoExtractor.selectTrack(videoTrackIndex)
            while (true) {
                val sampleSize = videoExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(muxVideoTrack, buffer, bufferInfo)
                videoExtractor.advance()
            }

            audioExtractor.selectTrack(audioTrackIndex)
            while (true) {
                val sampleSize = audioExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                bufferInfo.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(muxAudioTrack, buffer, bufferInfo)
                audioExtractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null

            videoExtractor.release()
            audioExtractor.release()

            val muxFile = File(tempMuxPath)
            if (muxFile.exists() && muxFile.length() > 1024L) {
                videoFile.delete()
                muxFile.renameTo(videoFile)
                audioFile.delete()
                true
            } else {
                muxFile.delete()
                false
            }
        } catch (_: Exception) {
            runCatching { muxer?.release() }
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
            runCatching { File(tempMuxPath).delete() }
            false
        } finally {
            runCatching { audioFile.delete() }
        }
    }

    private fun clearActive() { prefs.edit().remove("active_uri").remove("active_path").remove("active_name").remove("active_pid").commit() }
    private suspend fun recover() {
        val path = prefs.getString("active_path", null) ?: return
        val name = prefs.getString("active_name", null) ?: return
        val uri = prefs.getString("active_uri", null)?.let(Uri::parse)
        val output = Output(path, name, uri)
        val recoveringPid = prefs.getInt("active_pid", 0)
        signal("INT", recoveringPid, output)
        for (attempt in 0 until 10) {
            if (recoveringPid <= 1 || !Shell.cmd("kill -0 $recoveringPid 2>/dev/null").exec().isSuccess) break
            delay(500)
        }
        signal("KILL", recoveringPid, output)
        if (uri != null) runCatching { finalizeOutput(output, true) }
        clearActive()
    }
}
