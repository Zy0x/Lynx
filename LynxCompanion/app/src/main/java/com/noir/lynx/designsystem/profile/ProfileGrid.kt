package com.noir.lynx.designsystem.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.R
import com.noir.lynx.designsystem.theme.AccentBlue
import com.noir.lynx.designsystem.theme.AccentCyan
import com.noir.lynx.designsystem.theme.AccentOrange
import com.noir.lynx.designsystem.theme.AccentRed
import com.noir.lynx.designsystem.theme.BgElevated
import com.noir.lynx.designsystem.theme.BorderSubtle
import com.noir.lynx.designsystem.theme.TextPrimary
import com.noir.lynx.designsystem.theme.TextSecondary

/**
 * ProfileGrid — Luxury Bento Profile Selector with i18n support.
 */
@Composable
fun ProfileGrid(
    currentProfile: String,
    onProfileSelected: (String) -> Unit,
    onExtremeConfirmRequired: () -> Unit,
) {
    val profiles = listOf(
        ProfileItem(
            id = "auto",
            name = stringResource(R.string.profile_auto),
            desc = stringResource(R.string.profile_auto_desc),
            icon = Icons.Default.Bolt,
            color = AccentCyan
        ),
        ProfileItem(
            id = "balance",
            name = stringResource(R.string.profile_balance),
            desc = stringResource(R.string.profile_balance_desc),
            icon = Icons.Default.Tune,
            color = AccentBlue
        ),
        ProfileItem(
            id = "performance",
            name = stringResource(R.string.profile_performance),
            desc = stringResource(R.string.profile_performance_desc),
            icon = Icons.Default.Speed,
            color = AccentOrange
        ),
        ProfileItem(
            id = "extreme",
            name = stringResource(R.string.profile_extreme),
            desc = stringResource(R.string.profile_extreme_desc),
            icon = Icons.Default.LocalFireDepartment,
            color = AccentRed
        ),
        ProfileItem(
            id = "powersave",
            name = stringResource(R.string.profile_powersave),
            desc = stringResource(R.string.profile_powersave_desc),
            icon = Icons.Default.BatteryChargingFull,
            color = Color(0xFF00E676)
        ),
    )

    var lastProfileSwitchTime by remember { mutableLongStateOf(0L) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Hero top card for Auto AI
        val autoProfile = profiles.first()
        val isAutoActive = currentProfile == autoProfile.id
        ProfileBentoTile(
            item = autoProfile,
            isActive = isAutoActive,
            isWide = true,
            onClick = {
                val now = System.currentTimeMillis()
                if (now - lastProfileSwitchTime >= 400L) {
                    lastProfileSwitchTime = now
                    onProfileSelected(autoProfile.id)
                }
            }
        )

        // 2x2 Grid for Balance, Perf, Extreme, Powersave
        val rest = profiles.drop(1)
        rest.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { item ->
                    val isActive = currentProfile == item.id
                    ProfileBentoTile(
                        item = item,
                        isActive = isActive,
                        isWide = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastProfileSwitchTime >= 400L) {
                                lastProfileSwitchTime = now
                                if (item.id == "extreme" && currentProfile != "extreme") {
                                    onExtremeConfirmRequired()
                                } else {
                                    onProfileSelected(item.id)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private data class ProfileItem(
    val id: String,
    val name: String,
    val desc: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
private fun ProfileBentoTile(
    item: ProfileItem,
    isActive: Boolean,
    isWide: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) item.color.copy(alpha = 0.12f) else BgElevated,
        border = BorderStroke(
            width = if (isActive) 1.2.dp else 0.8.dp,
            color = if (isActive) item.color.copy(alpha = 0.6f) else BorderSubtle
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(if (isWide) 64.dp else 74.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isActive) item.color.copy(alpha = 0.2f) else Color(0x0FFFFFFF),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (isActive) item.color else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = item.name,
                        fontSize = 13.5.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isActive) item.color else TextPrimary,
                    )
                    Text(
                        text = item.desc,
                        fontSize = 10.sp,
                        color = TextSecondary,
                        maxLines = 1,
                    )
                }
            }

            if (isActive) {
                Surface(
                    shape = CircleShape,
                    color = item.color,
                    modifier = Modifier
                        .size(8.dp)
                        .padding(end = 2.dp)
                ) {}
            }
        }
    }
}
