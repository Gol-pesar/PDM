package com.example.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.CategoryEntity
import com.example.data.model.DownloadPriority
import com.example.data.model.QueueEntity
import com.example.ui.ProbeUiState
import com.example.ui.components.categoryColorFor
import com.example.ui.components.categoryIconFor
import com.example.ui.theme.MonoMetricSmall
import com.example.ui.theme.StatusCrimson
import com.example.ui.theme.StatusEmerald
import com.example.util.FormatUtils
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadSheet(
    initialUrl: String,
    categories: List<CategoryEntity>,
    queues: List<QueueEntity>,
    defaultThreadCount: Int,
    probeState: ProbeUiState,
    onProbeUrl: (url: String, referer: String, ua: String, auth: String) -> Unit,
    onClearProbe: () -> Unit,
    onDismiss: () -> Unit,
    onSubmitSingle: (
        url: String,
        fileName: String,
        categoryId: String,
        queueId: Long,
        priority: DownloadPriority,
        threadCount: Int,
        speedLimitKbps: Int,
        referer: String,
        customUserAgent: String,
        authHeader: String,
        startImmediately: Boolean,
        probedSize: Long,
        probedMime: String,
        probedSupportsResume: Boolean,
        probedEtag: String
    ) -> Unit,
    onSubmitBatch: (
        rawLines: String,
        queueId: Long,
        priority: DownloadPriority,
        threadCount: Int,
        startImmediately: Boolean
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current

    var isBatchMode by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf(initialUrl) }
    var fileName by remember {
        mutableStateOf(if (initialUrl.isNotBlank()) FormatUtils.extractFileNameFromUrl(initialUrl) else "")
    }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: "other") }
    var selectedQueueId by remember { mutableLongStateOf(queues.firstOrNull()?.id ?: 1L) }
    var priority by remember { mutableStateOf(DownloadPriority.NORMAL) }
    var threadCount by remember { mutableIntStateOf(defaultThreadCount) }
    var showMoreOptions by remember { mutableStateOf(false) }
    var referer by remember { mutableStateOf("") }
    var customUa by remember { mutableStateOf("") }
    var authHeader by remember { mutableStateOf("") }
    var speedLimitText by remember { mutableStateOf("") }
    var batchText by remember { mutableStateOf("") }

    LaunchedEffect(probeState.result) {
        val res = probeState.result ?: return@LaunchedEffect
        if (res.error == null) {
            if (res.fileName.isNotBlank()) fileName = res.fileName
            if (res.suggestedCategoryId.isNotBlank()) selectedCategoryId = res.suggestedCategoryId
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            onClearProbe()
            onDismiss()
        },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBatchMode) "افزودن چند لینک هم‌زمان" else "دانلود جدید",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { isBatchMode = !isBatchMode }) {
                    Icon(
                        imageVector = Icons.Filled.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isBatchMode) "لینک تکی" else "چند لینک")
                }
            }

            if (!isBatchMode) {
                // Clean URL Field + Paste Button
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = {
                            url = it
                            if (fileName.isBlank() && FormatUtils.isValidHttpUrl(it)) {
                                fileName = FormatUtils.extractFileNameFromUrl(it)
                            }
                        },
                        label = { Text("لینک فایل (https://...)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_download_url_input"),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clip = clipboard.getText()?.text?.trim().orEmpty()
                                    if (clip.isNotBlank()) {
                                        url = clip
                                        if (FormatUtils.isValidHttpUrl(clip)) {
                                            fileName = FormatUtils.extractFileNameFromUrl(clip)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentPaste,
                                    contentDescription = "جای‌گذاری لینک",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }

                // Quick Probe Pill (only shown when URL is valid)
                AnimatedVisibility(visible = FormatUtils.isValidHttpUrl(url)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onProbeUrl(url, referer, customUa, authHeader) },
                            enabled = !probeState.isProbing,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("probe_url_button")
                        ) {
                            if (probeState.isProbing) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("بررسی حجم و نام فایل", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                // Compact Probe Result Badge
                probeState.result?.let { res ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (res.error != null) StatusCrimson.copy(alpha = 0.08f)
                        else StatusEmerald.copy(alpha = 0.10f),
                        border = BorderStroke(
                            1.dp,
                            if (res.error != null) StatusCrimson.copy(alpha = 0.35f)
                            else StatusEmerald.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (res.error != null) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = if (res.error != null) StatusCrimson else StatusEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = res.error ?: buildString {
                                    append("حجم: ${FormatUtils.formatBytes(res.contentLength)}")
                                    if (res.supportsResume) append(" • قابلیت ادامه دانلود")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (res.error != null) StatusCrimson else StatusEmerald,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Editable File Name
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("نام فایل") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_download_filename_input")
                )

                // Colorful Category Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it.id }) { cat ->
                        val accent = categoryColorFor(cat.iconKey)
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            leadingIcon = {
                                Icon(
                                    imageVector = categoryIconFor(cat.iconKey),
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(cat.nameFa, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // Collapsible Extra Options (keeps the sheet uncluttered!)
                TextButton(
                    onClick = { showMoreOptions = !showMoreOptions },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = if (showMoreOptions) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("گزینه‌های بیشتر (صف شبانه، سرعت، هدر)")
                }

                AnimatedVisibility(visible = showMoreOptions) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("انتخاب صف", style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(queues, key = { it.id }) { queue ->
                                FilterChip(
                                    selected = selectedQueueId == queue.id,
                                    onClick = { selectedQueueId = queue.id },
                                    label = { Text(queue.nameFa, style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("تعداد رشته‌های دانلود", style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = "$threadCount بخش",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = threadCount.toFloat(),
                            onValueChange = { threadCount = it.roundToInt().coerceIn(1, 16) },
                            valueRange = 1f..16f,
                            steps = 14
                        )

                        OutlinedTextField(
                            value = speedLimitText,
                            onValueChange = { speedLimitText = it.filter(Char::isDigit).take(6) },
                            label = { Text("سقف سرعت برای این فایل (KB/s)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = referer,
                            onValueChange = { referer = it },
                            label = { Text("Referer (اختیاری)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = authHeader,
                            onValueChange = { authHeader = it },
                            label = { Text("Authorization (اختیاری)") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Clean Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val res = probeState.result
                            onSubmitSingle(
                                url, fileName, selectedCategoryId, selectedQueueId,
                                priority, threadCount, speedLimitText.toIntOrNull() ?: 0,
                                referer, customUa, authHeader, false,
                                res?.contentLength ?: -1L, res?.mimeType.orEmpty(),
                                res?.supportsResume ?: false, res?.etag.orEmpty()
                            )
                            onClearProbe()
                            onDismiss()
                        },
                        enabled = FormatUtils.isValidHttpUrl(url),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_to_queue_button")
                    ) {
                        Text("بعداً دانلود کن")
                    }

                    Button(
                        onClick = {
                            val res = probeState.result
                            onSubmitSingle(
                                url, fileName, selectedCategoryId, selectedQueueId,
                                priority, threadCount, speedLimitText.toIntOrNull() ?: 0,
                                referer, customUa, authHeader, true,
                                res?.contentLength ?: -1L, res?.mimeType.orEmpty(),
                                res?.supportsResume ?: false, res?.etag.orEmpty()
                            )
                            onClearProbe()
                            onDismiss()
                        },
                        enabled = FormatUtils.isValidHttpUrl(url),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("start_download_now_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("شروع دانلود", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Friendly Batch URL Mode
                Text(
                    text = "هر لینک رو توی یک خط بنویس یا از کلیپ‌بورد بچسبون؛ بقیه‌اش با ما!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = batchText,
                        onValueChange = { batchText = it },
                        label = { Text("https://example.com/file1.zip\nhttps://example.com/photo.jpg") },
                        minLines = 4,
                        maxLines = 7,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("batch_urls_input")
                    )
                }

                val validCount = remember(batchText) {
                    batchText.lines().count { FormatUtils.isValidHttpUrl(it) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val clip = clipboard.getText()?.text.orEmpty()
                            if (clip.isNotBlank()) {
                                batchText = if (batchText.isBlank()) clip else "$batchText\n$clip"
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("جای‌گذاری")
                    }

                    Text(
                        text = "$validCount لینک معتبر",
                        style = MonoMetricSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        onSubmitBatch(batchText, selectedQueueId, priority, threadCount, true)
                        onDismiss()
                    },
                    enabled = validCount > 0,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("start_batch_download_button")
                ) {
                    Text("شروع دانلود $validCount فایل", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
