package com.noir.lynx.feature.thermal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.hardware.VendorThermalConfigManager
import kotlinx.coroutines.launch

/**
 * VendorThermalRawEditorSheet — Modal Bottom Sheet for Power Users.
 * Provides direct access to view and edit raw vendor thermal INI configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorThermalRawEditorSheet(
    initialContent: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var textValue by remember { mutableStateOf(initialContent) }
    var isSaving by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgCard,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp),
                shape = RoundedCornerShape(2.dp),
                color = BorderSubtle
            ) {}
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Editor Raw INI Konfigurasi Vendor",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sunting langsung berkas konfigurasi termal OEM.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Tutup",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (feedbackMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentCyan.copy(alpha = 0.12f),
                    border = BorderStroke(0.6.dp, AccentCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = feedbackMessage.orEmpty(),
                        color = AccentCyan,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Monospace Editor Field
            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextPrimary,
                    lineHeight = 15.sp
                ),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BgSurfaceLowest,
                    unfocusedContainerColor = BgSurfaceLowest,
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = BorderSubtle,
                    cursorColor = AccentCyan
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
            )

            // Bottom Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.7.dp, BorderSubtle),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text("Batal", color = TextSecondary, fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        isSaving = true
                        coroutineScope.launch {
                            val res = VendorThermalConfigManager.applyRawContent(textValue, isLive = true)
                            if (res.isSuccess) {
                                feedbackMessage = "Perubahan berhasil diterapkan secara Live Bind Mount!"
                                kotlinx.coroutines.delay(800)
                                onDismiss()
                            } else {
                                feedbackMessage = "Gagal menerapkan: ${res.exceptionOrNull()?.message}"
                            }
                            isSaving = false
                        }
                    },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isSaving) "Menerapkan..." else "Terapkan Live",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
