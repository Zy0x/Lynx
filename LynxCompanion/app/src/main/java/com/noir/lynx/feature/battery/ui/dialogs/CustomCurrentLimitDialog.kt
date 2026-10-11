package com.noir.lynx.feature.battery.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.BgCard
import com.noir.lynx.designsystem.theme.BorderSubtle
import com.noir.lynx.designsystem.theme.TextPrimary
import com.noir.lynx.designsystem.theme.TextSecondary

/**
 * CustomCurrentLimitDialog — Dialog for entering a custom manual mA current limit with i18n support.
 */
@Composable
fun CustomCurrentLimitDialog(
    currentLimitMa: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var textVal by remember { mutableStateOf(currentLimitMa.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.battery_custom_current_title), fontWeight = FontWeight.Bold, color = TextPrimary) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.battery_custom_current_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = textVal,
                    onValueChange = { if (it.all { ch -> ch.isDigit() } && it.length <= 6) textVal = it },
                    label = { Text("Batas Arus (mA)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderSubtle,
                        focusedLabelColor = AccentCyan,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Contoh: 8000 (8A), 12000 (12A), 18000 (18A)",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ma = textVal.toIntOrNull() ?: currentLimitMa
                    onConfirm(ma)
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.Black)
            ) {
                Text(stringResource(R.string.action_apply), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = TextSecondary)
            }
        },
        containerColor = BgCard,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary
    )
}
