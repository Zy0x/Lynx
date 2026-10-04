package com.noir.lynx.lab

object DropCorrelator {

    fun correlate(current: FrameSample, previous: FrameSample, peakGpuMhz: Int): String {
        return when {
            current.tempC >= 46.0f && current.gpuClockMhz < (peakGpuMhz * 0.85) -> {
                "Thermal Throttling Aktif (Suhu %.1f°C memicu penurunan clock GPU)".format(current.tempC)
            }
            previous.gpuClockMhz > 0 && current.gpuClockMhz < (previous.gpuClockMhz * 0.75) -> {
                "Penurunan Frekuensi GPU (%d MHz → %d MHz oleh governor)".format(previous.gpuClockMhz, current.gpuClockMhz)
            }
            current.gpuLoadPct >= 92 -> {
                "Beban Grafis Penuh (%d%% GPU Utilization - Render Queue Stalled)".format(current.gpuLoadPct)
            }
            current.gpuLoadPct < 40 -> {
                "Stutter Non-GPU (%d%% Load - Bottleneck CPU / Thread UI / Garbage Collection)".format(current.gpuLoadPct)
            }
            else -> {
                "Keterlambatan Penyerahan Buffer Frame ke SurfaceFlinger"
            }
        }
    }

    fun generateRecommendation(
        dropEvents: List<DropEvent>,
        avgFps: Float,
        peakGpuMhz: Int,
        targetHz: Int
    ): String {
        if (dropEvents.isEmpty() || avgFps >= (targetHz * 0.95f)) {
            return "Performa rendering sangat optimal dan stabil. Tidak diperlukan modifikasi frekuensi atau profil khusus."
        }

        val clockDropCount = dropEvents.count { it.suspectedCause.contains("Penurunan Frekuensi") }
        val thermalCount = dropEvents.count { it.suspectedCause.contains("Thermal Throttling") }
        val gpuBoundCount = dropEvents.count { it.suspectedCause.contains("Beban Grafis Penuh") }
        val nonGpuCount = dropEvents.count { it.suspectedCause.contains("Non-GPU") }

        return when {
            thermalCount > 0 -> {
                "Terdeteksi %d event thermal throttling. Disarankan menggunakan kipas pendingin (cooler) atau menurunkan sedikit refresh rate ke 90Hz/60Hz agar kestabilan frame tetap terjaga tanpa pemanasan berlebih.".format(thermalCount)
            }
            clockDropCount >= dropEvents.size / 2 -> {
                val suggestedMin = (peakGpuMhz * 0.65).toInt()
                "Sebagian besar frame drop (%d kali) dipicu oleh clock GPU yang turun saat gameplay. Disarankan mengunci Frekuensi Min ke level ~%d MHz di profil game ini agar frekuensi tidak kolaps.".format(clockDropCount, suggestedMin)
            }
            gpuBoundCount >= dropEvents.size / 2 -> {
                "GPU beroperasi pada kapasitas puncak (90%+ load). Aktifkan Level 2 GPU Boost atau kunci frekuensi maksimum untuk menghilangkan stutter grafis.".format(gpuBoundCount)
            }
            nonGpuCount >= dropEvents.size / 2 -> {
                "Sebagian besar stutter terjadi saat beban GPU rendah (%d kali). Penyebab berasal dari CPU thread game atau I/O storage, bukan dari subsistem GPU.".format(nonGpuCount)
            }
            else -> {
                "Frame pacing stabil dengan variasi minor. Mengaktifkan SurfaceFlinger Latch Unsignaled dapat memotong input lag sentuh."
            }
        }
    }
}
