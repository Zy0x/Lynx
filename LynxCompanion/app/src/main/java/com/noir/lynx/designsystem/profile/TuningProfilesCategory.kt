package com.noir.lynx.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCard
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.designsystem.profile.*

/**
 * TuningProfilesCategory — Full Kernel Performance Profile Management Subscreen.
 *
 * Provides a dedicated space within the Tuning Hub for:
 * - Viewing all built-in and user-created custom profiles
 * - Creating, duplicating, editing, and deleting profiles
 * - Managing domain overrides and restoring default configurations
 */
@Composable
fun TuningProfilesCategory(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onExtremeConfirmRequired: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = uiState.state
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Descriptive Header Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = BgElevated,
            border = BorderStroke(0.8.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Katalog & Editor Preset Performa",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "liga 0, dlig 0, calt 0")
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Sesuaikan domain CPU, GPU, Thermal, dan Pengisian Daya per-profil. Anda dapat membuat preset kustom tanpa batasan.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        // Main Profile Catalog Card
        LynxCard(
            title = "Katalog Profil Kernel",
            icon = Icons.Default.Speed,
            accentColor = AccentOrange
        ) {
            ProfileGrid(
                catalog = uiState.kernelProfileCatalog,
                currentProfile = state.activeProfile,
                hasOverrides = state.profileOverrides.isNotEmpty(),
                onProfileSelected = { id, clearOverrides ->
                    viewModel.selectKernelPerformanceProfile(id, context, clearOverrides)
                },
                onExtremeConfirmRequired = onExtremeConfirmRequired,
                onProfileSaved = { profile, replaceBuiltIn, replaceCustom ->
                    viewModel.saveKernelPerformanceProfile(profile, replaceBuiltIn, replaceCustom)
                },
                onProfileDeleted = { id ->
                    viewModel.deleteKernelPerformanceProfile(id)
                },
                onProfileDuplicated = { id ->
                    viewModel.duplicateKernelPerformanceProfile(id)
                },
                onRestoreBuiltIn = { id ->
                    viewModel.restoreBuiltInKernelProfile(id, context)
                }
            )

            if (state.profileOverrides.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val overrides = state.profileOverrides.entries
                    .sortedBy { it.key.storageKey }
                    .joinToString(" • ") { (domain, profile) -> "${domain.storageKey.uppercase()}: ${profile.uppercase()}" }
                Text(
                    text = "Induk ${state.activeProfile.uppercase()} · override lokal $overrides",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}
