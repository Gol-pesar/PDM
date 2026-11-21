package com.example.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.ChecksumVerificationState
import com.example.data.model.DownloadEntity
import com.example.data.model.DownloadSegmentEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTelemetry
import com.example.ui.components.SegmentConnectionVisualizer
import com.example.ui.components.statusColorFor
import com.example.ui.theme.MonoMetricSmall
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDetailSheet(
    download: DownloadEntity,
    categoryName: String,
    segments: List<DownloadSegmentEntity>,
    telemetry: DownloadTelemetry?,
    checksumState: ChecksumVerificationState?,
    onDismiss: () -> Unit,
    onTogglePauseResume: () -> Unit,
    onOpenFile: () -> Unit,
    onShareFile: () -> Unit,
    onExportPublic: () -> Unit,
    onCalculateChecksum: () -> Unit,
    onVerifyExpectedHash: (String) -> Unit,
    onUpdateExpiredLink: (String) -> Unit,
    onDelete: (deleteFile: Boolean) -> Unit,
    onCopySuccess: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val status = download.statusEnum

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showReplaceLinkDialog by remember { mutableStateOf(false) }
    var showTechDetails by remember { mutableStateOf(false) }
    var expectedHashInput by remember(download.id) { mutableStateOf("") }

    val sha256Text = checksumState?.sha256?.ifBlank { download.sha256 } ?: download.sha256
    val md5Text = checksumState?.md5?.ifBlank { download.md5 } ?: download.md5

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Clean File Header
            Column {
                Text(
                    text = download.fileName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = statusColorFor(status).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = status.labelFa,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColorFor(status),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val dlBytes = telemetry?.liveDownloadedBytes?.takeIf { it > 0L } ?: download.downloadedBytes
                    val totBytes = telemetry?.liveTotalBytes?.takeIf { it > 0L } ?: download.totalBytes
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = if (totBytes > 0L) FormatUtils.formatBytes(totBytes) else FormatUtils.formatBytes(dlBytes),
                            style = MonoMetricSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Primary Friendly Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (status == DownloadStatus.COMPLETED) {
                    Button(
                        onClick = onOpenFile,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("باز کردن")
                    }
                    OutlinedButton(
                        onClick = onShareFile,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اشتراک")
                    }
                } else {
                    Button(
                        onClick = onTogglePauseResume,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val isRunning = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.CONNECTING
                        Icon(
                            imageVector = if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRunning) "توقف" else "ادامه دانلود")
                    }
                    OutlinedButton(
                        onClick = { showReplaceLinkDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تغییر لینک")
                    }
                }
            }

            // Quick Utility Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(download.url))
                        onCopySuccess("لینک کپی شد")
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("کپی لینک")
                }

                if (status == DownloadStatus.COMPLETED && !download.exportedToPublic) {
                    OutlinedButton(
                        onClick = onExportPublic,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ذخیره در Downloads")
                    }
                }
            }

            // Collapsible Technical Details & Integrity Check (cleanly tucked away!)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTechDetails = !showTechDetails },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "اطلاعات فنی و بررسی سلامت فایل",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = if (showTechDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null
                        )
                    }

                    AnimatedVisibility(visible = showTechDetails) {
                        Column(
                            modifier = Modifier.padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (segments.isNotEmpty()) {
                                Text(
                                    text = "بخش‌های دانلود (${segments.size} رشته)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                SegmentConnectionVisualizer(segments = segments)
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }

                            SpecLine("تعداد رشته‌ها", "${download.threadCount}")
                            SpecLine("ادامه دانلود", if (download.supportsResume) "دارد" else "ندارد")
                            SpecLine("زمان ثبت", FormatUtils.formatTimestamp(download.createdAt))
                            SpecLine("لینک", download.url, isMono = true)

                            if (status == DownloadStatus.COMPLETED) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VerifiedUser,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "بررسی سلامت فایل (SHA-256 / MD5)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (checksumState?.isCalculating == true) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Text("در حال محاسبه...", style = MaterialTheme.typography.bodySmall)
                                    }
                                } else if (sha256Text.isNotBlank()) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text("SHA-256: $sha256Text", style = MonoMetricSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("MD5: $md5Text", style = MonoMetricSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = onCalculateChecksum,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("calculate_checksum_button")
                                    ) {
                                        Text("محاسبه کد هش فایل")
                                    }
                                }

                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    OutlinedTextField(
                                        value = expectedHashInput,
                                        onValueChange = { expectedHashInput = it },
                                        label = { Text("کد هش مرجع برای مقایسه") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onVerifyExpectedHash(expectedHashInput) },
                                    enabled = expectedHashInput.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.testTag("verify_checksum_button")
                                ) {
                                    Text("مقایسه کد هش")
                                }

                                checksumState?.isMatch?.let { matched ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (matched) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                            contentDescription = null,
                                            tint = if (matched) StatusEmerald else StatusCrimson,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (matched) "فایل کاملاً سالم و معتبره!" else "کد هش مطابقت نداره!",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (matched) StatusEmerald else StatusCrimson,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Delete Action
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, StatusCrimson.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusCrimson),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delete_download_button")
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("حذف دانلود")
            }
        }
    }

    if (showDeleteDialog) {
        var deleteFromDisk by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text("حذف فایل") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("مطمئنی می‌خوای «${download.fileName}» رو حذف کنی؟")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deleteFromDisk, onCheckedChange = { deleteFromDisk = it })
                        Text("فایل اصلی هم از حافظه پاک بشه", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(deleteFromDisk)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCrimson),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("حذف کن")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("بی‌خیال") }
            }
        )
    }

    if (showReplaceLinkDialog) {
        var newUrl by remember { mutableStateOf(download.url) }
        AlertDialog(
            onDismissRequest = { showReplaceLinkDialog = false },
            shape = RoundedCornerShape(20.dp),
            title = { Text("تغییر لینک دانلود") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "اگر لینک قبلی منقضی شده، لینک جدید رو اینجا بذار تا دانلود از ادامه‌اش انجام بشه.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        OutlinedTextField(
                            value = newUrl,
                            onValueChange = { newUrl = it },
                            label = { Text("لینک جدید") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReplaceLinkDialog = false
                        onUpdateExpiredLink(newUrl)
                    },
                    enabled = FormatUtils.isValidHttpUrl(newUrl),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("ادامه دانلود")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceLinkDialog = false }) { Text("بی‌خیال") }
            }
        )
    }
}

@Composable
private fun SpecLine(label: String, value: String, isMono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        if (isMono) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = value,
                    style = MonoMetricSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
        } else {
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
        }
    }
}
