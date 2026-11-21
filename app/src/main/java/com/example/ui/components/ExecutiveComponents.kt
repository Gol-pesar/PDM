package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.DownloadEntity
import com.example.data.model.DownloadSegmentEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTelemetry
import com.example.ui.theme.CatArchivesAmber
import com.example.ui.theme.CatAudioEmerald
import com.example.ui.theme.CatDocsIndigo
import com.example.ui.theme.CatImagesRose
import com.example.ui.theme.CatOtherTeal
import com.example.ui.theme.CatProgramsViolet
import com.example.ui.theme.CatVideoCoral
import com.example.ui.theme.MonoMetricMedium
import com.example.ui.theme.MonoMetricSmall
import com.example.ui.theme.PdmBrandCoral
import com.example.ui.theme.PdmBrandTeal
import com.example.ui.theme.StatusActiveSky
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.util.FormatUtils

fun categoryIconFor(iconKey: String): ImageVector = when (iconKey) {
    "documents" -> Icons.Filled.Description
    "archives" -> Icons.Filled.FolderZip
    "programs" -> Icons.Filled.SettingsApplications
    "images" -> Icons.Filled.Image
    "video" -> Icons.Filled.VideoFile
    "audio" -> Icons.Filled.AudioFile
    "custom" -> Icons.Filled.Folder
    else -> Icons.Filled.InsertDriveFile
}

fun categoryColorFor(iconKey: String): Color = when (iconKey) {
    "documents" -> CatDocsIndigo
    "archives" -> CatArchivesAmber
    "programs" -> CatProgramsViolet
    "images" -> CatImagesRose
    "video" -> CatVideoCoral
    "audio" -> CatAudioEmerald
    else -> CatOtherTeal
}

@Composable
fun statusColorFor(status: DownloadStatus): Color = when (status) {
    DownloadStatus.DOWNLOADING, DownloadStatus.CONNECTING -> StatusActiveSky
    DownloadStatus.COMPLETED -> StatusEmerald
    DownloadStatus.PAUSED -> StatusAmber
    DownloadStatus.FAILED, DownloadStatus.CANCELED -> StatusCrimson
    DownloadStatus.QUEUED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun PdmTopHeader(
    activeCount: Int,
    aggregateSpeedBps: Long,
    showSearchAction: Boolean,
    isSearchOpen: Boolean,
    onToggleSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_logo),
                    contentDescription = stringResource(R.string.cd_brand_logo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Column {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.app_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (activeCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = FormatUtils.formatSpeed(aggregateSpeedBps),
                                style = MonoMetricSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                if (showSearchAction) {
                    Surface(
                        shape = CircleShape,
                        color = if (isSearchOpen) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        IconButton(
                            onClick = onToggleSearch,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("toggle_search_button")
                        ) {
                            Icon(
                                imageVector = if (isSearchOpen) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = stringResource(R.string.cd_search),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveTransferBanner(
    activeCount: Int,
    aggregateSpeedBps: Long,
    onPauseAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(PdmBrandTeal, StatusActiveSky)
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "$activeCount فایل در حال دانلود",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = FormatUtils.formatSpeed(aggregateSpeedBps),
                            style = MonoMetricMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.92f)
                        )
                    }
                }
            }

            TextButton(
                onClick = onPauseAll,
                modifier = Modifier.testTag("pause_all_button")
            ) {
                Text(
                    text = "توقف همه",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DownloadItemCard(
    download: DownloadEntity,
    categoryName: String,
    categoryIconKey: String,
    telemetry: DownloadTelemetry?,
    onTogglePauseResume: () -> Unit,
    onOpenOrInspect: () -> Unit,
    onOpenFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = download.statusEnum
    val statusColor = statusColorFor(status)
    val catColor = categoryColorFor(categoryIconKey)
    val liveDownloaded = telemetry?.liveDownloadedBytes?.takeIf { it > 0L } ?: download.downloadedBytes
    val liveTotal = telemetry?.liveTotalBytes?.takeIf { it > 0L } ?: download.totalBytes
    val progress = when {
        status == DownloadStatus.COMPLETED -> 1f
        liveTotal > 0L -> (liveDownloaded.toFloat() / liveTotal.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("download_card_${download.id}")
            .clickable(onClick = onOpenOrInspect),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (status == DownloadStatus.DOWNLOADING) {
                PdmBrandTeal.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Colorful Minimal Category Badge
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = catColor.copy(alpha = 0.14f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = categoryIconFor(categoryIconKey),
                            contentDescription = categoryName,
                            tint = catColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (status == DownloadStatus.COMPLETED) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = StatusEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "تکمیل شد",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusEmerald,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (status == DownloadStatus.FAILED) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = StatusCrimson,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "خطا در دانلود",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusCrimson,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = status.labelFa,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = if (status == DownloadStatus.COMPLETED) {
                                    FormatUtils.formatBytes(liveTotal.coerceAtLeast(liveDownloaded))
                                } else if (liveTotal > 0L) {
                                    "${FormatUtils.formatBytes(liveDownloaded)} / ${FormatUtils.formatBytes(liveTotal)}"
                                } else {
                                    FormatUtils.formatBytes(liveDownloaded)
                                },
                                style = MonoMetricSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Clean Circular Action Button (48dp touch target)
                val (icon, tint, bg, desc, tag) = when (status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.CONNECTING -> ActionVisual(
                        Icons.Filled.Pause, PdmBrandCoral, PdmBrandCoral.copy(alpha = 0.14f),
                        "توقف دانلود", "pause_button_${download.id}"
                    )
                    DownloadStatus.COMPLETED -> ActionVisual(
                        Icons.Filled.OpenInNew, StatusEmerald, StatusEmerald.copy(alpha = 0.14f),
                        "باز کردن فایل", "open_button_${download.id}"
                    )
                    DownloadStatus.FAILED -> ActionVisual(
                        Icons.Filled.Refresh, StatusCrimson, StatusCrimson.copy(alpha = 0.14f),
                        "تلاش مجدد", "retry_button_${download.id}"
                    )
                    else -> ActionVisual(
                        Icons.Filled.PlayArrow, PdmBrandTeal, PdmBrandTeal.copy(alpha = 0.14f),
                        "ادامه دانلود", "resume_button_${download.id}"
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = bg,
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(
                        onClick = if (status == DownloadStatus.COMPLETED) onOpenFile else onTogglePauseResume,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(tag)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = desc,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (status != DownloadStatus.COMPLETED) {
                Spacer(modifier = Modifier.height(10.dp))
                if (status == DownloadStatus.CONNECTING || (status == DownloadStatus.DOWNLOADING && liveTotal <= 0L)) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = statusColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = statusColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }

                if (status == DownloadStatus.DOWNLOADING && telemetry != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = FormatUtils.formatSpeed(telemetry.speedBytesPerSec),
                                style = MonoMetricSmall,
                                fontWeight = FontWeight.Bold,
                                color = PdmBrandTeal
                            )
                            Text(
                                text = if (telemetry.etaSeconds >= 0L) {
                                    "${FormatUtils.formatPercent(progress)}  •  ${FormatUtils.formatEta(telemetry.etaSeconds)}"
                                } else {
                                    FormatUtils.formatPercent(progress)
                                },
                                style = MonoMetricSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (download.errorMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = download.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status == DownloadStatus.FAILED) StatusCrimson else StatusAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private data class ActionVisual(
    val icon: ImageVector,
    val tint: Color,
    val bg: Color,
    val contentDesc: String,
    val tag: String
)

@Composable
fun SegmentConnectionVisualizer(
    segments: List<DownloadSegmentEntity>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            segments.forEach { seg ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(seg.progressFraction)
                            .fillMaxSize()
                            .background(if (seg.isCompleted) StatusEmerald else PdmBrandTeal)
                    )
                }
            }
        }
        segments.forEach { seg ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "بخش ${seg.segmentIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(42.dp)
                )
                LinearProgressIndicator(
                    progress = { seg.progressFraction },
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (seg.isCompleted) StatusEmerald else StatusActiveSky,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = FormatUtils.formatPercent(seg.progressFraction),
                        style = MonoMetricSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
