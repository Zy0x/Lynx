package com.noir.lynx.feature.hud.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.feature.hud.combo.MacroCombo
import com.noir.lynx.feature.hud.combo.TouchShareParser
import com.noir.lynx.core.locale.LynxText as Text

@Composable
internal fun HudComboShare(combo: MacroCombo, editable: Boolean, onUpdate: (MacroCombo) -> Unit) {
    val context = LocalContext.current
    var code by rememberSaveable(combo.id) { mutableStateOf("") }
    val copied = hudString(R.string.hud_touchshare_copied)
    val success = hudString(R.string.hud_touchshare_success)
    val invalid = hudString(R.string.hud_touchshare_invalid)
    val clipboardLabel = hudString(R.string.hud_magic_title)
    HudAction(hudString(R.string.hud_touchshare_export), enabled = combo.taps.isNotEmpty(), onClick = {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
            ClipData.newPlainText(clipboardLabel, TouchShareParser.exportToString(combo)))
            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth())
    Text(hudString(R.string.hud_touchshare_dialog_title), fontSize = 12.sp, color = HudMuted)
    OutlinedTextField(code, { code = it }, enabled = editable,
        label = { Text(hudString(R.string.hud_combo_code)) }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
    HudAction(hudString(R.string.hud_touchshare_import), enabled = editable && code.isNotBlank(), onClick = {
            val imported = TouchShareParser.importFromString(code, combo.id)
            if (imported != null) { onUpdate(imported); code = "" }
            Toast.makeText(context, if (imported != null) success else invalid, Toast.LENGTH_SHORT).show()
        }, modifier = Modifier.fillMaxWidth())
}
