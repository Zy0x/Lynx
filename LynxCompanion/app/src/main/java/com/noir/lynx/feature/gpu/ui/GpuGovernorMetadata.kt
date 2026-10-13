package com.noir.lynx.feature.gpu.ui

import java.util.Locale
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import com.noir.lynx.designsystem.controls.SafeSlider as Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.*
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.card.*
import com.noir.lynx.designsystem.controls.*
import com.noir.lynx.designsystem.tweak.*
import com.noir.lynx.designsystem.profile.*
import com.noir.lynx.feature.navigation.model.TuningCategory

// ============================================================
//  Live Hardware Telemetry Card — Luxury Bento-Box Layout
// ============================================================

import com.noir.lynx.ui.*

fun formatGpuGovernorLabel(gov: String, platform: String): String {
    val clean = gov.trim()
    val isMtk = platform.contains("mali", ignoreCase = true) || clean in listOf("0", "1", "2")
    return when {
        isMtk && clean == "0" -> "0 • Dinamis (Bawaan GED)"
        isMtk && clean == "1" -> "1 • Performa (Low-Latency)"
        isMtk && clean == "2" -> "2 • Agresif (Kustom)"
        clean.contains("msm-adreno-tz", ignoreCase = true) -> "msm-adreno-tz (TrustZone AI)"
        clean.contains("performance", ignoreCase = true) -> "performance (Maksimal)"
        clean.contains("simple_ondemand", ignoreCase = true) -> "simple_ondemand (Responsif)"
        clean.contains("powersave", ignoreCase = true) -> "powersave (Hemat Daya)"
        clean.contains("msm-cpufreq", ignoreCase = true) -> "msm-cpufreq (Sinkron CPU)"
        else -> clean
    }
}

fun getGpuGovernorDescription(gov: String, platform: String): String {
    val clean = gov.trim()
    val isMtk = platform.contains("mali", ignoreCase = true) || clean in listOf("0", "1", "2")
    return when {
        isMtk && clean == "0" -> "Mode Default GED: Pengendalian frekuensi GPU dinamis berdasarkan estimasi beban frame display buffer. Seimbang untuk efisiensi daya dan stabilitas suhu."
        isMtk && clean == "1" -> "Mode Performa GED: Memaksa driver memprioritaskan frame-rate konsisten dan memotong latency switching frekuensi, ideal untuk game kompetitif."
        isMtk && clean == "2" -> "Mode Agresif Kustom: Memaksa profil beban GPU agresif untuk mempertahankan clock frekuensi menengah ke atas demi mencegah frame-drop micro-stutter."
        clean.contains("msm-adreno-tz", ignoreCase = true) -> "Governor TrustZone Qualcomm QTI: Algoritma cerdas yang memantau utilitas komputasi Adreno via secure kernel enclave."
        clean.contains("performance", ignoreCase = true) -> "Mengunci frekuensi GPU Adreno pada clock tertinggi yang diizinkan tanpa downclocking idle."
        clean.contains("simple_ondemand", ignoreCase = true) -> "Menaikkan frekuensi seketika saat ada beban grafis dan turun saat idle secara responsif."
        clean.contains("powersave", ignoreCase = true) -> "Mengunci clock GPU pada level minimum untuk menghemat konsumsi baterai ekstrem."
        else -> "Governor pengatur manajemen daya dan frekuensi GPU saat runtime."
    }
}
