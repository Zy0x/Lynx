package com.noir.lynx.core.locale

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as Material3Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.res.stringResource
import com.noir.lynx.R

private data class DynamicUiText(val pattern: Regex, val resource: Int)

/** Patterns for legacy Compose labels whose values are interpolated at runtime. */
private val dynamicUiTextResources = listOf(
    DynamicUiText(Regex("^Profil dasar: (.+)$"), R.string.dynamic_profile_base),
    DynamicUiText(Regex("^CPU: (.+) · ubah$"), R.string.dynamic_profile_cpu),
    DynamicUiText(Regex("^GPU: (.+) · ubah$"), R.string.dynamic_profile_gpu),
    DynamicUiText(Regex("^([☑□]) Sertakan kebijakan termal saat ini$"), R.string.dynamic_profile_include_thermal),
    DynamicUiText(Regex("^([☑□]) Sertakan batas pengisian saat ini$"), R.string.dynamic_profile_include_battery),
    DynamicUiText(Regex("^Kustomisasi (.+) akan dihapus dan nilai bawaan Lynx dipulihkan\\.$"), R.string.dynamic_profile_delete_confirmation),
    DynamicUiText(Regex("^Kustomisasi (.+)$"), R.string.dynamic_profile_customize_title),
    DynamicUiText(Regex("^Anda dapat menimpa (.+) atau menyimpan perubahan sebagai profil baru\\.$"), R.string.dynamic_profile_overwrite_confirmation),
    DynamicUiText(Regex("^Profil Kustom Aktif: (.+)$"), R.string.dynamic_profile_active),
    DynamicUiText(Regex("^Basis: (.+) • (.+)$"), R.string.dynamic_profile_base_description),
    DynamicUiText(Regex("^Induk (.+) · override lokal (.+)$"), R.string.dynamic_profile_parent_overrides),
    DynamicUiText(Regex("^Mode (.+) Aktif$"), R.string.dynamic_mode_active),
    DynamicUiText(Regex("^Apakah Anda yakin ingin memulihkan partisi dari backup '(.+)'\\?$"), R.string.dynamic_backup_restore_confirmation),
    DynamicUiText(Regex("^(\\d+) Inti \\((.+)\\) • (.+)-(.*) MHz$"), R.string.dynamic_cpu_cluster_summary),
    DynamicUiText(Regex("^Pilih Frekuensi (Minimum|Maksimum)$"), R.string.dynamic_cpu_frequency_picker),
    DynamicUiText(Regex("^Policy (.+) \\((.+) tunables ditemukan\\)$"), R.string.dynamic_cpu_policy_tunables),
    DynamicUiText(Regex("^Policy (.+) \\((.+)\\)$"), R.string.dynamic_cpu_policy),
    DynamicUiText(Regex("^Parameter Lanjutan: (.+)$"), R.string.dynamic_cpu_advanced_parameters),
    DynamicUiText(Regex("^Nilai \\((.+)\\)$"), R.string.dynamic_cpu_value_unit),
    DynamicUiText(Regex("^Driver: (.+) • Kontrol status tidur inti CPU$"), R.string.dynamic_cpu_idle_driver),
    DynamicUiText(Regex("^Ketuk core \\(C0-C(.+)\\) pada masing-masing kelompok tugas untuk mengizinkan atau melarang thread berjalan pada inti fisik tersebut\\.$"), R.string.dynamic_cpu_core_instructions),
    DynamicUiText(Regex("^TABEL LENGKAP STEPPING FREKUENSI \\((.+) OPP\\)$"), R.string.dynamic_cpu_frequency_table),
    DynamicUiText(Regex("^Perizinan: (.+)$"), R.string.dynamic_cpu_permissions),
    DynamicUiText(Regex("^STATUS INTI CPU \\((.+) CORES\\)$"), R.string.dynamic_cpu_core_status),
    DynamicUiText(Regex("^Fitur Kernel Aktif: (.+)$"), R.string.dynamic_gpu_active_features),
    DynamicUiText(Regex("^(.+) node kernel terverifikasi • Akses I/O langsung$"), R.string.dynamic_gpu_verified_nodes),
    DynamicUiText(Regex("^Tersedia (.+) step frekuensi clock OPP$"), R.string.dynamic_gpu_clock_steps),
    DynamicUiText(Regex("^(.+) Kontrol$"), R.string.dynamic_gpu_control_count),
    DynamicUiText(Regex("^Driver Fisik • (.+)$"), R.string.dynamic_gpu_physical_driver),
    DynamicUiText(Regex("^Shield Anti-Throttling OEM \\((.+)\\)$"), R.string.dynamic_oem_throttling_shield),
    DynamicUiText(Regex("^\\((.+)\\)"), R.string.dynamic_count_in_parentheses),
    DynamicUiText(Regex("^Tersedia: (.+) MB$"), R.string.dynamic_memory_available),
    DynamicUiText(Regex("^Bebas: (.+) MB$"), R.string.dynamic_memory_free),
    DynamicUiText(Regex("^Ukuran Alokasi ZRAM \\(Skala RAM Fisik: (.+) MB\\)$"), R.string.dynamic_zram_allocation_scale),
    DynamicUiText(Regex("^Muat Lebih Banyak \\((.+) tersisa\\)$"), R.string.dynamic_load_more),
    DynamicUiText(Regex("^Terapkan \\((.+)\\)$"), R.string.dynamic_apply_value),
    DynamicUiText(Regex("^DISTRIBUSI FRAMETIME \\((.+) FRAMES\\)$"), R.string.dynamic_benchmark_frame_distribution),
    DynamicUiText(Regex("^Nilai saat ini: (.+)$"), R.string.dynamic_current_value),
    DynamicUiText(Regex("^(.+) \\(Nilai saat ini: (.+)\\)$"), R.string.dynamic_feature_current_value),
    DynamicUiText(Regex("^Efek: (.+)$"), R.string.dynamic_effect_description),
    DynamicUiText(Regex("^Detik ke-(.+)s$"), R.string.dynamic_seconds_offset),
    DynamicUiText(Regex("^Kondisi: (.+) MHz • (.+)% Load • (.+)°C$"), R.string.dynamic_gpu_event_conditions),
    DynamicUiText(Regex("^Riwayat Sesi Rekaman \\((.+)/20\\)$"), R.string.dynamic_recorded_session_history),
    DynamicUiText(Regex("^Metrik Sesi: (.+)$"), R.string.dynamic_session_metrics),
    DynamicUiText(Regex("^Pukul (.+) • Durasi (.+)s • (.+) Frame$"), R.string.dynamic_session_time_summary),
    DynamicUiText(Regex("^Reset ke (.+)$"), R.string.dynamic_reset_to_label),
    DynamicUiText(Regex("^Terapkan Offset Voltase \\((.+) mV\\)$"), R.string.dynamic_apply_voltage_offset),
    DynamicUiText(Regex("^(.+) Tingkatan$"), R.string.dynamic_cpu_frequency_level_count),
    DynamicUiText(Regex("^(.+) Cores$"), R.string.dynamic_cpu_core_count),
    DynamicUiText(Regex("^Edit Tunable: (.+)$"), R.string.dynamic_edit_tunable),
    DynamicUiText(Regex("^Node: (.+)$"), R.string.dynamic_node_label),
    DynamicUiText(Regex("^Legacy Minfree Levels: (.+)$"), R.string.dynamic_legacy_minfree_levels),
    DynamicUiText(Regex("^Read Wakeup Threshold: (.+) bits$"), R.string.dynamic_read_wakeup_threshold),
    DynamicUiText(Regex("^Write Wakeup Threshold: (.+) bits$"), R.string.dynamic_write_wakeup_threshold),
    DynamicUiText(Regex("^Headphone Gain \\(L/R\\): (.+)dB / (.+)dB$"), R.string.dynamic_headphone_gain),
    DynamicUiText(Regex("^Speaker Gain: (.+)dB$"), R.string.dynamic_speaker_gain),
    DynamicUiText(Regex("^Microphone Gain: (.+)dB$"), R.string.dynamic_microphone_gain),
    DynamicUiText(Regex("^Driver: (.+) • Kernel C-States$"), R.string.dynamic_cpu_driver_cstates),
)

private val recommendationUiTextResources = mapOf(
    "Sangat Disarankan [ON] untuk respons bidikan instan" to R.string.recommendation_gaming_instant_response,
    "[OFF] atau [ON] sesuai preferensi kelancaran UI" to R.string.recommendation_balance_ui_smoothness,
    "[OFF] untuk efisiensi daya saat navigasi ringan" to R.string.recommendation_battery_light_navigation,
    "Sangat Disarankan [ON] untuk mencegah frame drop mendadak" to R.string.recommendation_gaming_prevent_drops,
    "[OFF] untuk manajemen arus standar pabrik" to R.string.recommendation_balance_oem_current,
    "[OFF] untuk menjaga konsumsi puncak baterai" to R.string.recommendation_battery_peak_draw,
    "[OFF] dengan cooler eksternal, atau [ON] tanpa cooler" to R.string.recommendation_thermal_cooler,
    "Sangat Disarankan [ON] untuk keamanan suhu harian" to R.string.recommendation_thermal_daily_safety,
    "[ON] efisiensi termal maksimal" to R.string.recommendation_thermal_efficiency,
)

/** Localize static accessibility labels with the same catalog used by visible text. */
@Composable
fun localizedUiText(source: String): String {
    legacyUiTextResources[source]?.let { return stringResource(it) }
    recommendationUiTextResources[source]?.let { return stringResource(it) }
    for (entry in dynamicUiTextResources) {
        val match = entry.pattern.matchEntire(source) ?: continue
        return stringResource(entry.resource, *match.groupValues.drop(1).toTypedArray())
    }
    return source
}

/** Compatibility bridge while the legacy UI literals are moved into explicit string resources. */
@Composable
fun LynxText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    val localized = localizedUiText(text)
    Material3Text(
        text = localized,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

@Composable
fun LynxText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, androidx.compose.foundation.text.InlineTextContent> = mapOf(),
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
) {
    Material3Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        inlineContent = inlineContent,
        onTextLayout = onTextLayout,
        style = style
    )
}

