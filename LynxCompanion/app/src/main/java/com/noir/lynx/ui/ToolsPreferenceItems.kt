package com.noir.lynx.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.core.locale.AppLocale
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.feature.media.ui.MediaDecoderCard

fun LazyListScope.toolsPreferenceItems() {
    item(key = "language_settings") {
        val context = LocalContext.current
        val language = AppLocale.selected(context)
        LynxCard(
            title = stringResource(R.string.language_settings_title),
            icon = Icons.Default.Language,
            accentColor = AccentCyan
        ) {
            Text(
                stringResource(R.string.language_settings_desc),
                color = TextSecondary,
                fontSize = 11.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    AppLocale.SYSTEM to R.string.language_system,
                    AppLocale.INDONESIAN to R.string.language_indonesian,
                    AppLocale.ENGLISH to R.string.language_english
                ).forEach { (tag, label) ->
                    FilterChip(
                        selected = language == tag,
                        onClick = {
                            AppLocale.setSelected(context, tag)
                            (context as? ComponentActivity)?.recreate()
                        },
                        label = { Text(stringResource(label), fontSize = 10.sp) }
                    )
                }
            }
        }
    }
    item(key = "media_decoder_settings") { MediaDecoderCard() }
}
