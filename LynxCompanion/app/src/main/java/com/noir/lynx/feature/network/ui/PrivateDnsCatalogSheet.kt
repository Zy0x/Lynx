package com.noir.lynx.feature.network.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noir.lynx.core.locale.LynxText as Text
import com.noir.lynx.data.LynxUiState
import com.noir.lynx.designsystem.card.LynxCapabilityBadge
import com.noir.lynx.designsystem.card.LynxSectionDivider
import com.noir.lynx.designsystem.card.LynxSectionHeader
import com.noir.lynx.designsystem.theme.*
import com.noir.lynx.ui.LynxViewModel
import com.noir.lynx.ui.benchmarkDnsLatency
import com.noir.lynx.ui.setPrivateDns
import java.util.Locale

internal data class DnsResolverSpec(
    val id: String,
    val name: String,
    val shortName: String,
    val hostname: String,
    val categoryId: String,
    val categoryLabel: String,
    val description: String,
    val accent: Color
)

internal val PRIVATE_DNS_CATALOG: List<DnsResolverSpec> = listOf(
    // Kecepatan & Gaming
    DnsResolverSpec("cf_fast", "Cloudflare (1.1.1.1)", "Cloudflare", "1dot1dot1dot1.cloudflare-dns.com", "speed", "Gaming & Cepat", "Latensi ultra-rendah global tanpa filter konten", AccentCyan),
    DnsResolverSpec("google", "Google Public DNS", "Google", "dns.google", "speed", "Gaming & Cepat", "Routing Anycast global stabil & kompatibel luas", AccentCyan),
    DnsResolverSpec("opendns", "Cisco OpenDNS", "OpenDNS", "dns.opendns.com", "speed", "Gaming & Cepat", "Resolver cepat Cisco dengan proteksi phishing dasar", AccentCyan),
    DnsResolverSpec("dnssb", "DNS.SB (Anycast)", "DNS.SB", "dns.sb", "speed", "Gaming & Cepat", "Anycast ringan bebas log & ramah koneksi Asia/Eropa", AccentCyan),
    // Anti-Iklan & Tracker
    DnsResolverSpec("adguard", "AdGuard DNS", "AdGuard", "dns.adguard-dns.com", "adblock", "Anti-Iklan", "Blokir iklan dalam aplikasi, tracker & domain analitik", AccentGreen),
    DnsResolverSpec("controld_ads", "Control D Free AdBlock", "Control D Ads", "p2.freedns.controld.com", "adblock", "Anti-Iklan", "Filter iklan, tracker & malware tanpa perlu akun", AccentGreen),
    DnsResolverSpec("mullvad_ads", "Mullvad AdBlock", "Mullvad Ads", "adblock.dns.mullvad.net", "adblock", "Anti-Iklan", "Blokir iklan + tracker ketat berbasis zero-logging", AccentGreen),
    DnsResolverSpec("mullvad_ext", "Mullvad Extended", "Mullvad Ext", "extended.dns.mullvad.net", "adblock", "Anti-Iklan", "AdBlock + tracker + pemblokiran domain sosial/telemetry", AccentGreen),
    // Keamanan & Malware
    DnsResolverSpec("quad9", "Quad9 Security", "Quad9", "dns.quad9.net", "security", "Keamanan", "Proteksi malware & phishing berdasar threat-intel DNSSEC", AccentBlue),
    DnsResolverSpec("cf_sec", "Cloudflare Security", "CF Malware", "security.cloudflare-dns.com", "security", "Keamanan", "Kecepatan Cloudflare + blokir otomatis situs malware", AccentBlue),
    DnsResolverSpec("controld_mal", "Control D Malware", "Control D Sec", "p1.freedns.controld.com", "security", "Keamanan", "Filter ancaman malware & situs berbahaya berkecepatan tinggi", AccentBlue),
    DnsResolverSpec("cb_sec", "CleanBrowsing Security", "CB Security", "security-filter-dns.cleanbrowsing.org", "security", "Keamanan", "Blokir malware, ransomware & phishing tanpa filter umum", AccentBlue),
    // Privasi & Tanpa Filter
    DnsResolverSpec("mullvad_base", "Mullvad Unfiltered", "Mullvad", "dns.mullvad.net", "privacy", "Privasi", "No-log, bebas filter & mendukung QNAME minimization", AccentPurple),
    DnsResolverSpec("adguard_raw", "AdGuard Unfiltered", "AdGuard Raw", "unfiltered.adguard-dns.com", "privacy", "Privasi", "Hanya enkripsi DoT AdGuard tanpa penyaringan iklan/konten", AccentPurple),
    DnsResolverSpec("controld_raw", "Control D Unfiltered", "Control D Raw", "p0.freedns.controld.com", "privacy", "Privasi", "Resolver Anycast cepat tanpa blokir untuk bebas akses penuh", AccentPurple),
    // Keluarga / SafeSearch
    DnsResolverSpec("cf_family", "Cloudflare Family", "CF Family", "family.cloudflare-dns.com", "family", "Keluarga", "Blokir malware + konten dewasa untuk keamanan keluarga", AccentOrange),
    DnsResolverSpec("adguard_family", "AdGuard Family", "AG Family", "dns-family.adguard-dns.com", "family", "Keluarga", "Blokir iklan, konten dewasa & paksa SafeSearch aktif", AccentOrange),
    DnsResolverSpec("cb_family", "CleanBrowsing Family", "CB Family", "family-filter-dns.cleanbrowsing.org", "family", "Keluarga", "Filter ketat ramah anak + SafeSearch YouTube/Google", AccentOrange)
)

internal fun findDnsCatalogEntry(hostname: String): DnsResolverSpec? =
    PRIVATE_DNS_CATALOG.firstOrNull { it.hostname.equals(hostname.trim(), ignoreCase = true) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrivateDnsCatalogBottomSheet(
    uiState: LynxUiState,
    viewModel: LynxViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp

    var selectedCategory by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var customDnsInput by remember(uiState.privateDnsSpecifier) {
        mutableStateOf(
            if (uiState.privateDnsMode == "hostname" &&
                findDnsCatalogEntry(uiState.privateDnsSpecifier) == null
            ) uiState.privateDnsSpecifier else ""
        )
    }

    val categoryChips = remember {
        listOf(
            "all" to "Semua (${PRIVATE_DNS_CATALOG.size})",
            "speed" to "Gaming & Cepat",
            "adblock" to "Anti-Iklan",
            "security" to "Keamanan",
            "privacy" to "Privasi",
            "family" to "Keluarga"
        )
    }

    val filteredCatalog = remember(selectedCategory, searchQuery) {
        val q = searchQuery.trim().lowercase()
        PRIVATE_DNS_CATALOG.filter { item ->
            val catMatch = selectedCategory == "all" || item.categoryId == selectedCategory
            val qMatch = q.isBlank() ||
                item.name.lowercase().contains(q) ||
                item.hostname.lowercase().contains(q) ||
                item.description.lowercase().contains(q)
            catMatch && qMatch
        }
    }

    val allCatalogHosts = remember { PRIVATE_DNS_CATALOG.map { it.hostname } }
    val filteredHosts = remember(filteredCatalog) { filteredCatalog.map { it.hostname } }

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
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Katalog Private DNS (DoT)",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${PRIVATE_DNS_CATALOG.size} resolver terenkripsi Port 853 • Uji latensi real-time",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextSecondary)
                }
            }

            HorizontalDivider(color = BorderGlass, thickness = 0.6.dp, modifier = Modifier.padding(top = 6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Benchmark buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = { viewModel.benchmarkDnsLatency(allCatalogHosts, autoSelectFastest = false) },
                        enabled = !uiState.isTestingDns,
                        shape = RoundedCornerShape(10.dp),
                        color = BgElevated,
                        border = BorderStroke(0.7.dp, AccentBlue.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f).heightIn(min = 46.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (uiState.isTestingDns) "Menguji..." else "Uji Latensi",
                                color = AccentBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            val targets = filteredHosts.ifEmpty { allCatalogHosts }
                            viewModel.benchmarkDnsLatency(targets, autoSelectFastest = true)
                        },
                        enabled = !uiState.isTestingDns,
                        shape = RoundedCornerShape(10.dp),
                        color = AccentCyan.copy(alpha = 0.14f),
                        border = BorderStroke(0.8.dp, AccentCyan.copy(alpha = 0.45f)),
                        modifier = Modifier.weight(1.3f).heightIn(min = 46.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (uiState.isTestingDns) "Memilih..." else "Uji & Pilih Tercepat",
                                color = AccentCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Compact search input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Hapus", tint = TextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    } else null,
                    placeholder = { Text("Cari nama DNS, AdBlock, Quad9, Mullvad...", color = TextSecondary, fontSize = 11.5.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderGlass,
                        focusedContainerColor = Color(0xFF0A0C12),
                        unfocusedContainerColor = Color(0xFF0A0C12)
                    )
                )

                // Horizontal category chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categoryChips.forEach { (catId, label) ->
                        val selected = selectedCategory == catId
                        Surface(
                            onClick = { selectedCategory = catId },
                            shape = RoundedCornerShape(9.dp),
                            color = if (selected) AccentCyan.copy(alpha = 0.16f) else BgSurfaceLowest,
                            border = BorderStroke(
                                if (selected) 1.1.dp else 0.7.dp,
                                if (selected) AccentCyan else BorderSubtle
                            ),
                            modifier = Modifier.heightIn(min = 38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) AccentCyan else TextSecondary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                // Filtered list
                if (filteredCatalog.isEmpty()) {
                    Text(
                        text = "Tidak ada DNS yang cocok dengan pencarian \"$searchQuery\". Gunakan input Custom Hostname di bawah jika diperlukan.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        filteredCatalog.forEach { entry ->
                            val isSelected = uiState.privateDnsMode == "hostname" &&
                                uiState.privateDnsSpecifier.equals(entry.hostname, ignoreCase = true)
                            val rtt = uiState.dnsLatencyMs[entry.hostname]
                            Surface(
                                onClick = { viewModel.setPrivateDns("hostname", entry.hostname) },
                                shape = RoundedCornerShape(11.dp),
                                color = if (isSelected) entry.accent.copy(alpha = 0.15f) else BgSurfaceLowest,
                                border = BorderStroke(
                                    if (isSelected) 1.3.dp else 0.7.dp,
                                    if (isSelected) entry.accent else BorderSubtle
                                ),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = entry.name,
                                                color = if (isSelected) TextPrimary else TextPrimary.copy(alpha = 0.92f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (selectedCategory == "all") {
                                                LynxCapabilityBadge(entry.categoryLabel.uppercase(), entry.accent)
                                            }
                                        }
                                        Text(
                                            text = entry.hostname,
                                            color = entry.accent.copy(alpha = 0.88f),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = entry.description,
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (rtt != null) {
                                            val rttText = if (rtt > 0f) "${"%.0f".format(Locale.US, rtt)} ms" else "Timeout"
                                            val rttColor = when {
                                                rtt <= 0f -> AccentRed
                                                rtt <= 45f -> AccentGreen
                                                else -> AccentOrange
                                            }
                                            LynxCapabilityBadge(rttText, rttColor)
                                        }
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = "Aktif", tint = entry.accent, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                LynxSectionDivider()

                LynxSectionHeader(
                    title = "Custom Hostname DoT",
                    subtitle = "Masukkan alamat TLS pribadi (NextDNS ID, ControlD kustom, atau server sendiri)",
                    accentColor = AccentBlue
                )

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customDnsInput,
                        onValueChange = { customDnsInput = it.filter { ch -> !ch.isWhitespace() } },
                        singleLine = true,
                        placeholder = { Text("contoh: abcdef.dns.nextdns.io", color = TextSecondary, fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = TextPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderGlass,
                            focusedContainerColor = Color(0xFF0A0C12),
                            unfocusedContainerColor = Color(0xFF0A0C12)
                        )
                    )
                    Button(
                        onClick = {
                            val target = customDnsInput.trim()
                            if (target.isNotBlank()) viewModel.setPrivateDns("hostname", target)
                        },
                        enabled = customDnsInput.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan.copy(alpha = 0.2f),
                            contentColor = AccentCyan
                        ),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.45f)),
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text("Terapkan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
