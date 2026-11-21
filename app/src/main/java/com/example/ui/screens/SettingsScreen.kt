package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AppSettings
import com.example.data.model.QueueEntity
import com.example.data.model.ThemePreference
import com.example.data.repository.StorageInfo
import com.example.ui.theme.CatDocsIndigo
import com.example.ui.theme.PdmBrandCoral
import com.example.ui.theme.PdmBrandTeal
import com.example.ui.theme.StatusActiveSky
import com.example.util.FormatUtils
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    nightQueue: QueueEntity?,
    storageInfo: StorageInfo,
    onUpdateTheme: (ThemePreference) -> Unit,
    onUpdateDefaultThreads: (Int) -> Unit,
    onUpdateMaxConcurrent: (Int) -> Unit,
    onUpdateGlobalSpeedLimit: (Int) -> Unit,
    onUpdateWifiOnly: (Boolean) -> Unit,
    onUpdateLowBatteryPause: (Boolean) -> Unit,
    onUpdateAutoRetryCount: (Int) -> Unit,
    onUpdateAutoExportPublic: (Boolean) -> Unit,
    onUpdateUserAgent: (String) -> Unit,
    onUpdateProxy: (enabled: Boolean, host: String, port: Int) -> Unit,
    onUpdateNightQueue: (QueueEntity) -> Unit,
    onCleanOrphanedFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvanced by remember { mutableStateOf(false) }
    var userAgentDraft by remember(settings.customUserAgent) { mutableStateOf(settings.customUserAgent) }
    var proxyHostDraft by remember(settings.proxyHost) { mutableStateOf(settings.proxyHost) }
    var proxyPortDraft by remember(settings.proxyPort) { mutableStateOf(settings.proxyPort.toString()) }
    var proxyEnabledDraft by remember(settings.proxyEnabled) { mutableStateOf(settings.proxyEnabled) }

    val speedPresets = listOf(
        0 to "نامحدود",
        512 to "512 KB/s",
        1024 to "1 MB/s",
        2048 to "2 MB/s",
        5120 to "5 MB/s"
    )

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Theme Picker
        item(key = "theme_card") {
            FriendlySettingsCard(
                title = "تم و ظاهر برنامه",
                icon = Icons.Filled.Palette,
                accent = PdmBrandCoral
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemePreference.entries.forEach { pref ->
                        val selected = settings.themePreference == pref
                        FilterChip(
                            selected = selected,
                            onClick = { onUpdateTheme(pref) },
                            label = {
                                Text(
                                    text = pref.labelFa,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_option_${pref.name.lowercase()}")
                        )
                    }
                }
            }
        }

        // Essential Download Preferences
        item(key = "download_basics") {
            FriendlySettingsCard(
                title = "سرعت و دانلود",
                icon = Icons.Filled.Speed,
                accent = PdmBrandTeal
            ) {
                FriendlySwitchRow(
                    title = "فقط با Wi-Fi دانلود کن",
                    subtitle = "وقتی اینترنت سیم‌کارت وصله، دانلودها متوقف می‌مونن",
                    checked = settings.wifiOnly,
                    onCheckedChange = onUpdateWifiOnly,
                    testTag = "wifi_only_switch"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                FriendlySwitchRow(
                    title = "ذخیره خودکار در پوشه Downloads",
                    subtitle = "فایل‌های دانلودشده مستقیم توی گالری و فایل‌منیجر دیده می‌شن",
                    checked = settings.autoExportPublicDownloads,
                    onCheckedChange = onUpdateAutoExportPublic,
                    testTag = "auto_export_switch"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Text(
                    text = "محدودیت سرعت: ${FormatUtils.formatSpeedLimit(settings.globalSpeedLimitKbps)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(speedPresets) { (kbps, label) ->
                        FilterChip(
                            selected = settings.globalSpeedLimitKbps == kbps,
                            onClick = { onUpdateGlobalSpeedLimit(kbps) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("تعداد رشته‌های سرعت‌دهنده", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${settings.defaultThreadCount} بخش",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = settings.defaultThreadCount.toFloat(),
                    onValueChange = { onUpdateDefaultThreads(it.roundToInt()) },
                    valueRange = 1f..16f,
                    steps = 14
                )
            }
        }

        // Simple Built-in Night Scheduler
        if (nightQueue != null) {
            item(key = "night_scheduler") {
                var startHourText by remember(nightQueue.startHour) { mutableStateOf(nightQueue.startHour.toString()) }
                var stopHourText by remember(nightQueue.stopHour) { mutableStateOf(nightQueue.stopHour.toString()) }

                FriendlySettingsCard(
                    title = "دانلود شبانه خودکار",
                    icon = Icons.Filled.NightsStay,
                    accent = CatDocsIndigo
                ) {
                    FriendlySwitchRow(
                        title = "فعال‌سازی زمان‌بندی شبانه",
                        subtitle = "فایل‌های «صف دانلود شبانه» سر ساعت شروع و متوقف می‌شن",
                        checked = nightQueue.scheduleEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateNightQueue(nightQueue.copy(scheduleEnabled = enabled))
                        },
                        testTag = "night_schedule_switch"
                    )

                    AnimatedVisibility(visible = nightQueue.scheduleEnabled) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = startHourText,
                                    onValueChange = { startHourText = it.filter(Char::isDigit).take(2) },
                                    label = { Text("ساعت شروع (0-23)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = stopHourText,
                                    onValueChange = { stopHourText = it.filter(Char::isDigit).take(2) },
                                    label = { Text("ساعت پایان (0-23)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    onUpdateNightQueue(
                                        nightQueue.copy(
                                            startHour = (startHourText.toIntOrNull() ?: 2).coerceIn(0, 23),
                                            stopHour = (stopHourText.toIntOrNull() ?: 7).coerceIn(0, 23)
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("ذخیره ساعت شبانه")
                            }
                        }
                    }
                }
            }
        }

        // Collapsible Advanced Section (keeps main settings super simple!)
        item(key = "advanced_section") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvanced = !showAdvanced },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = null,
                                tint = StatusActiveSky,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "تنظیمات پیشرفته",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = showAdvanced) {
                        Column(
                            modifier = Modifier.padding(top = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("دانلود هم‌زمان", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${settings.maxConcurrentDownloads} فایل",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = settings.maxConcurrentDownloads.toFloat(),
                                onValueChange = { onUpdateMaxConcurrent(it.roundToInt()) },
                                valueRange = 1f..5f,
                                steps = 3
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("تلاش مجدد خودکار", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${settings.autoRetryCount} بار",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = settings.autoRetryCount.toFloat(),
                                onValueChange = { onUpdateAutoRetryCount(it.roundToInt()) },
                                valueRange = 0f..10f,
                                steps = 9
                            )

                            FriendlySwitchRow(
                                title = "توقف در باتری کم",
                                subtitle = "وقتی شارژ زیر ۱۵٪ بره، دانلودها متوقف می‌شن",
                                checked = settings.lowBatteryPause,
                                onCheckedChange = onUpdateLowBatteryPause,
                                testTag = "battery_guard_switch"
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            FriendlySwitchRow(
                                title = "پروکسی HTTP",
                                subtitle = "استفاده از پروکسی دلخواه برای دانلود",
                                checked = proxyEnabledDraft,
                                onCheckedChange = { enabled ->
                                    proxyEnabledDraft = enabled
                                    if (!enabled) {
                                        onUpdateProxy(false, proxyHostDraft, proxyPortDraft.toIntOrNull() ?: 8080)
                                    }
                                },
                                testTag = "proxy_enabled_switch"
                            )

                            if (proxyEnabledDraft) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = proxyHostDraft,
                                        onValueChange = { proxyHostDraft = it },
                                        label = { Text("آدرس سرور") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(2f)
                                    )
                                    OutlinedTextField(
                                        value = proxyPortDraft,
                                        onValueChange = { proxyPortDraft = it.filter(Char::isDigit).take(5) },
                                        label = { Text("پورت") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Button(
                                    onClick = {
                                        onUpdateProxy(
                                            true,
                                            proxyHostDraft,
                                            (proxyPortDraft.toIntOrNull() ?: 8080).coerceIn(1, 65535)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("ذخیره پروکسی")
                                }
                            }

                            OutlinedTextField(
                                value = userAgentDraft,
                                onValueChange = { userAgentDraft = it },
                                label = { Text("User-Agent") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedButton(
                                onClick = { onUpdateUserAgent(userAgentDraft) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("ذخیره User-Agent")
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("پاکسازی فایل‌های اضافه", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "فضای فعلی: ${FormatUtils.formatBytes(storageInfo.usedByAppBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = onCleanOrphanedFiles,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("clean_orphans_button")
                                ) {
                                    Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("پاکسازی")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Brand Footer Card
        item(key = "pdm_about") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_pdm_logo_1795244640873),
                        contentDescription = "لوگوی PDM",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Column {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = "PDM",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Palang Download Manager • v1.0",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "ساده، سریع و همیشه آماده دانلود",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendlySettingsCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun FriendlySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
