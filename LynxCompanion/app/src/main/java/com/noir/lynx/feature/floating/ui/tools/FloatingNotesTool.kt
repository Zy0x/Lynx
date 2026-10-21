package com.noir.lynx.feature.floating.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FloatingNotesTool(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("lynx_floating_notes", Context.MODE_PRIVATE) }
    var notesText by remember { mutableStateOf(prefs.getString("saved_notes", "") ?: "") }

    fun save(text: String) {
        notesText = text
        prefs.edit().putString("saved_notes", text).apply()
    }

    fun appendMacro(macro: String) {
        val next = if (notesText.isEmpty()) macro else "$notesText\n$macro"
        save(next)
    }

    val macros = remember {
        listOf(
            "⚡ Flash (5m)" to "[Flash CD 5:00]",
            "👑 Lord (8m)" to "[Lord Spawn 8:00]",
            "🐢 Turtle (2m)" to "[Turtle 2:00]",
            "🛡️ Aegis/Cleanse" to "[Cleanse 2:00]",
            "⚔️ Ult (60s)" to "[Ult CD 60s]"
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C1017))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Quick Macro Chips Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time stamp macro
            Surface(
                onClick = {
                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                    appendMacro("⏰ [$time]")
                },
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                border = BorderStroke(0.6.dp, Color(0xFF00E5FF)),
                modifier = Modifier.height(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("+ Jam Sekarang", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                }
            }

            macros.forEach { (label, snippet) ->
                Surface(
                    onClick = { appendMacro(snippet) },
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF141D2B),
                    border = BorderStroke(0.6.dp, Color(0xFF223147)),
                    modifier = Modifier.height(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(label, fontSize = 9.5.sp, color = Color(0xFF94A3B8))
                    }
                }
            }
        }

        // Editable Note Paper
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF101722),
            border = BorderStroke(0.8.dp, Color(0xFF1F2B3D)),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                if (notesText.isEmpty()) {
                    Text(
                        text = "Catat waktu cooldown, formasi lawan, atau strategi game di sini...\n(Tersimpan otomatis)",
                        fontSize = 11.5.sp,
                        color = Color(0xFF45556C),
                        lineHeight = 16.sp
                    )
                }
                BasicTextField(
                    value = notesText,
                    onValueChange = ::save,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    ),
                    cursorBrush = SolidColor(Color(0xFF00E5FF)),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bottom Actions: Copy, Clear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${notesText.length} karakter",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF55657E)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Copy
                Surface(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Lynx Game Notes", notesText))
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF16202E),
                    border = BorderStroke(0.6.dp, Color(0xFF25344A)),
                    modifier = Modifier.height(26.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(12.dp))
                        Text("Salin", fontSize = 10.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                    }
                }

                // Clear
                Surface(
                    onClick = { save("") },
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF201616),
                    border = BorderStroke(0.6.dp, Color(0xFF422020)),
                    modifier = Modifier.height(26.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(12.dp))
                        Text("Hapus", fontSize = 10.sp, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
