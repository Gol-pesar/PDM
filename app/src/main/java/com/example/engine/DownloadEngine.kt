package com.example.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.example.data.model.AppSettings
import com.example.data.model.DownloadEntity
import com.example.data.model.DownloadSegmentEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTelemetry
import com.example.data.model.PostQueueAction
import com.example.data.model.UrlProbeResult
import com.example.data.prefs.SettingsRepository
import com.example.data.repository.DownloadRepository
import com.example.util.FormatUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class DownloadEngine(
    private val context: Context,
    private val repository: DownloadRepository,
    private val settingsRepository: SettingsRepository
) {
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val liveByteCounters = ConcurrentHashMap<Long, AtomicLong>()
    private val liveTotalSizes = ConcurrentHashMap<Long, Long>()
    private val activeThreadCounts = ConcurrentHashMap<Long, Int>()
    private val lastSampledBytes = ConcurrentHashMap<Long, Long>()

    private val _telemetryMap = MutableStateFlow<Map<Long, DownloadTelemetry>>(emptyMap())
    val telemetryMap: StateFlow<Map<Long, DownloadTelemetry>> = _telemetryMap.asStateFlow()

    private val _aggregateSpeedBytesPerSec = MutableStateFlow(0L)
    val aggregateSpeedBytesPerSec: StateFlow<Long> = _aggregateSpeedBytesPerSec.asStateFlow()

    private val _bandwidthHistory = MutableStateFlow(List(36) { 0L })
    val bandwidthHistory: StateFlow<List<Long>> = _bandwidthHistory.asStateFlow()

    @Volatile
    private var currentSettings: AppSettings = AppSettings()

    @Volatile
    private var foregroundRunning = false

    init {
        engineScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                currentSettings = settings
            }
        }
        startTelemetryLoop()
        startSchedulerLoop()
    }

    private fun buildHttpClient(settings: AppSettings = currentSettings): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        if (settings.proxyEnabled && settings.proxyHost.isNotBlank()) {
            runCatching {
                val proxy = Proxy(
                    Proxy.Type.HTTP,
                    InetSocketAddress(settings.proxyHost.trim(), settings.proxyPort)
                )
                builder.proxy(proxy)
            }
        }
        return builder.build()
    }

    private fun buildRequest(
        url: String,
        referer: String = "",
        customUserAgent: String = "",
        authHeader: String = "",
        rangeHeader: String? = null,
        headOnly: Boolean = false
    ): Request {
        val ua = customUserAgent.ifBlank { currentSettings.customUserAgent }
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", ua)
            .header("Accept", "*/*")
        if (referer.isNotBlank()) builder.header("Referer", referer)
        if (authHeader.isNotBlank()) builder.header("Authorization", authHeader)
        if (rangeHeader != null) builder.header("Range", rangeHeader)
        if (headOnly) builder.head() else builder.get()
        return builder.build()
    }

    suspend fun probeUrl(
        rawUrl: String,
        referer: String = "",
        customUserAgent: String = "",
        authHeader: String = ""
    ): UrlProbeResult = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (!FormatUtils.isValidHttpUrl(url)) {
            return@withContext UrlProbeResult(
                url = url,
                fileName = "",
                contentLength = -1L,
                mimeType = "application/octet-stream",
                supportsResume = false,
                host = "",
                suggestedCategoryId = "other",
                httpCode = 0,
                error = "نشانی اینترنتی معتبر نیست. لطفاً لینک را با http:// یا https:// وارد کنید."
            )
        }

        val client = buildHttpClient()
        try {
            // Try HEAD first
            val headReq = buildRequest(url, referer, customUserAgent, authHeader, headOnly = true)
            client.newCall(headReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val contentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    val acceptRanges = resp.header("Accept-Ranges").orEmpty()
                    val supportsResume = acceptRanges.contains("bytes", ignoreCase = true) && contentLength > 0L
                    val mime = resp.header("Content-Type")?.substringBefore(';')?.trim()
                        ?: "application/octet-stream"
                    val disposition = resp.header("Content-Disposition")
                    val etag = resp.header("ETag").orEmpty()
                    val fileName = FormatUtils.extractFileNameFromUrl(resp.request.url.toString(), disposition, mime)
                    val categoryId = repository.resolveCategoryForFile(fileName, mime)
                    return@withContext UrlProbeResult(
                        url = url,
                        fileName = fileName,
                        contentLength = contentLength,
                        mimeType = mime,
                        supportsResume = supportsResume,
                        host = FormatUtils.extractHost(resp.request.url.toString()),
                        suggestedCategoryId = categoryId,
                        etag = etag,
                        httpCode = resp.code
                    )
                }
            }

            // Fallback to Range GET 0-0 for servers that disallow HEAD
            val rangeReq = buildRequest(
                url = url,
                referer = referer,
                customUserAgent = customUserAgent,
                authHeader = authHeader,
                rangeHeader = "bytes=0-0",
                headOnly = false
            )
            client.newCall(rangeReq).execute().use { resp ->
                val isPartial = resp.code == 206
                if (resp.isSuccessful || isPartial) {
                    val contentRange = resp.header("Content-Range")
                    val totalFromRange = contentRange?.substringAfter('/')?.toLongOrNull() ?: -1L
                    val contentLength = if (totalFromRange > 0L) {
                        totalFromRange
                    } else {
                        resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    }
                    val supportsResume = isPartial ||
                        resp.header("Accept-Ranges").orEmpty().contains("bytes", ignoreCase = true)
                    val mime = resp.header("Content-Type")?.substringBefore(';')?.trim()
                        ?: "application/octet-stream"
                    val disposition = resp.header("Content-Disposition")
                    val etag = resp.header("ETag").orEmpty()
                    val fileName = FormatUtils.extractFileNameFromUrl(resp.request.url.toString(), disposition, mime)
                    val categoryId = repository.resolveCategoryForFile(fileName, mime)
                    return@withContext UrlProbeResult(
                        url = url,
                        fileName = fileName,
                        contentLength = contentLength,
                        mimeType = mime,
                        supportsResume = supportsResume && contentLength > 0L,
                        host = FormatUtils.extractHost(resp.request.url.toString()),
                        suggestedCategoryId = categoryId,
                        etag = etag,
                        httpCode = resp.code
                    )
                } else {
                    return@withContext UrlProbeResult(
                        url = url,
                        fileName = FormatUtils.extractFileNameFromUrl(url),
                        contentLength = -1L,
                        mimeType = "application/octet-stream",
                        supportsResume = false,
                        host = FormatUtils.extractHost(url),
                        suggestedCategoryId = "other",
                        httpCode = resp.code,
                        error = "پاسخ سرور: کد خطای HTTP ${resp.code}"
                    )
                }
            }
        } catch (e: Exception) {
            return@withContext UrlProbeResult(
                url = url,
                fileName = FormatUtils.extractFileNameFromUrl(url),
                contentLength = -1L,
                mimeType = "application/octet-stream",
                supportsResume = false,
                host = FormatUtils.extractHost(url),
                suggestedCategoryId = "other",
                httpCode = 0,
                error = "خطای اتصال به سرور: ${e.localizedMessage ?: "عدم دسترسی شبکه"}"
            )
        }
    }

    fun startOrResumeDownload(downloadId: Long) {
        engineScope.launch {
            val entity = repository.getDownloadById(downloadId) ?: return@launch
            if (activeJobs.containsKey(downloadId)) return@launch

            if (entity.statusEnum == DownloadStatus.COMPLETED) return@launch

            // Check concurrency cap
            val maxConcurrent = currentSettings.maxConcurrentDownloads.coerceIn(1, 5)
            if (activeJobs.size >= maxConcurrent) {
                repository.updateStatus(downloadId, DownloadStatus.QUEUED, "")
                return@launch
            }

            launchWorker(entity)
        }
    }

    fun pauseDownload(downloadId: Long) {
        engineScope.launch {
            activeJobs.remove(downloadId)?.cancel()
            cleanupLiveCounters(downloadId)
            val entity = repository.getDownloadById(downloadId)
            if (entity != null && entity.statusEnum != DownloadStatus.COMPLETED) {
                repository.updateStatus(downloadId, DownloadStatus.PAUSED, "")
            }
            dispatchQueuedDownloads()
        }
    }

    fun pauseAll() {
        engineScope.launch {
            val ids = activeJobs.keys.toList()
            ids.forEach { id ->
                activeJobs.remove(id)?.cancel()
                cleanupLiveCounters(id)
                repository.updateStatus(id, DownloadStatus.PAUSED, "")
            }
            val queued = repository.getDownloadsByStatuses(listOf(DownloadStatus.QUEUED))
            queued.forEach { repository.updateStatus(it.id, DownloadStatus.PAUSED, "") }
        }
    }

    fun resumeAll() {
        engineScope.launch {
            val resumable = repository.getDownloadsByStatuses(
                listOf(DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.QUEUED)
            )
            resumable.forEach { item ->
                repository.updateStatus(item.id, DownloadStatus.QUEUED, "")
            }
            dispatchQueuedDownloads()
        }
    }

    fun cancelDownload(downloadId: Long, deleteFile: Boolean) {
        engineScope.launch {
            activeJobs.remove(downloadId)?.cancel()
            cleanupLiveCounters(downloadId)
            val entity = repository.getDownloadById(downloadId)
            if (entity != null) {
                repository.deleteDownload(entity, deleteFileFromDisk = deleteFile)
            }
            dispatchQueuedDownloads()
        }
    }

    fun startQueue(queueId: Long) {
        engineScope.launch {
            repository.setQueueRunning(queueId, true)
            val items = repository.getQueueDownloadsByStatuses(
                queueId,
                listOf(DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.QUEUED)
            )
            items.forEach { repository.updateStatus(it.id, DownloadStatus.QUEUED, "") }
            dispatchQueuedDownloads()
        }
    }

    fun stopQueue(queueId: Long) {
        engineScope.launch {
            repository.setQueueRunning(queueId, false)
            val items = repository.getQueueDownloadsByStatuses(
                queueId,
                listOf(DownloadStatus.DOWNLOADING, DownloadStatus.CONNECTING, DownloadStatus.QUEUED)
            )
            items.forEach { item ->
                activeJobs.remove(item.id)?.cancel()
                cleanupLiveCounters(item.id)
                repository.updateStatus(item.id, DownloadStatus.PAUSED, "")
            }
        }
    }

    fun dispatchQueuedDownloads() {
        engineScope.launch {
            val maxConcurrent = currentSettings.maxConcurrentDownloads.coerceIn(1, 5)
            val availableSlots = maxConcurrent - activeJobs.size
            if (availableSlots <= 0) return@launch

            val queued = repository.getDownloadsByStatuses(listOf(DownloadStatus.QUEUED))
            var launched = 0
            for (item in queued) {
                if (launched >= availableSlots) break
                if (activeJobs.containsKey(item.id)) continue
                val queue = repository.getQueueById(item.queueId)
                // Default queue or running queue can dispatch immediately
                if (queue == null || queue.isRunning || queue.isDefault) {
                    launchWorker(item)
                    launched++
                }
            }
        }
    }

    private fun launchWorker(initialEntity: DownloadEntity) {
        val job = engineScope.launch {
            var entity = initialEntity
            try {
                // Policy Checks: Wi-Fi Only & Battery Guard
                if (currentSettings.wifiOnly && !isWifiConnected()) {
                    repository.updateStatus(
                        entity.id,
                        DownloadStatus.PAUSED,
                        "متوقف به دلیل سیاست «فقط شبکه Wi-Fi»"
                    )
                    return@launch
                }
                if (currentSettings.lowBatteryPause && isBatteryCriticallyLow()) {
                    repository.updateStatus(
                        entity.id,
                        DownloadStatus.PAUSED,
                        "متوقف به دلیل شارژ کم باتری (زیر ۱۵٪)"
                    )
                    return@launch
                }

                repository.updateStatus(entity.id, DownloadStatus.CONNECTING, "")

                // Ensure local path exists
                val targetFile = if (entity.localPath.isNotBlank()) {
                    File(entity.localPath).also { it.parentFile?.mkdirs() }
                } else {
                    repository.allocateLocalFile(entity.fileName, entity.categoryId)
                }

                // Refresh metadata if unknown size or first launch
                var totalBytes = entity.totalBytes
                var supportsResume = entity.supportsResume
                var mimeType = entity.mimeType
                var etag = entity.etag

                if (totalBytes <= 0L || entity.localPath.isBlank()) {
                    val probe = probeUrl(
                        rawUrl = entity.url,
                        referer = entity.referer,
                        customUserAgent = entity.customUserAgent,
                        authHeader = entity.authHeader
                    )
                    if (probe.error == null) {
                        if (probe.contentLength > 0L) totalBytes = probe.contentLength
                        supportsResume = probe.supportsResume
                        if (probe.mimeType.isNotBlank()) mimeType = probe.mimeType
                        if (probe.etag.isNotBlank()) etag = probe.etag
                    }
                }

                entity = entity.copy(
                    localPath = targetFile.absolutePath,
                    totalBytes = totalBytes,
                    supportsResume = supportsResume,
                    mimeType = mimeType,
                    etag = etag,
                    status = DownloadStatus.DOWNLOADING.name,
                    errorMessage = ""
                )
                repository.updateDownload(entity)

                liveTotalSizes[entity.id] = totalBytes
                val useMultiSegment = supportsResume &&
                    totalBytes >= MIN_MULTI_SEGMENT_BYTES &&
                    entity.threadCount > 1

                if (useMultiSegment) {
                    executeMultiSegmentDownload(entity, targetFile)
                } else {
                    executeSingleStreamDownload(entity, targetFile)
                }

                // Mark Completed
                val finalSize = targetFile.length().coerceAtLeast(entity.totalBytes)
                val completedEntity = entity.copy(
                    downloadedBytes = finalSize,
                    totalBytes = finalSize,
                    status = DownloadStatus.COMPLETED.name,
                    errorMessage = "",
                    completedAt = System.currentTimeMillis()
                )
                repository.updateDownload(completedEntity)

                // Auto-export to Public Downloads if enabled
                if (currentSettings.autoExportPublicDownloads) {
                    repository.exportToPublicDownloads(completedEntity)
                }

                // Check post-queue action if queue became empty
                checkQueueCompletion(entity.queueId)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                val maxRetries = currentSettings.autoRetryCount
                val refreshed = repository.getDownloadById(entity.id)
                if (refreshed != null && refreshed.retryCount < maxRetries) {
                    repository.updateDownload(
                        refreshed.copy(
                            retryCount = refreshed.retryCount + 1,
                            status = DownloadStatus.QUEUED.name,
                            errorMessage = "تلاش مجدد (${refreshed.retryCount + 1}/$maxRetries)..."
                        )
                    )
                    delay(2000L)
                } else {
                    repository.updateStatus(
                        entity.id,
                        DownloadStatus.FAILED,
                        e.localizedMessage ?: "خطای غیرمنتظره در حین دریافت فایل"
                    )
                }
            } finally {
                activeJobs.remove(entity.id)
                cleanupLiveCounters(entity.id)
                dispatchQueuedDownloads()
            }
        }
        activeJobs[initialEntity.id] = job
    }

    private suspend fun executeMultiSegmentDownload(
        entity: DownloadEntity,
        targetFile: File
    ) = coroutineScope {
        val totalBytes = entity.totalBytes
        var segments = repository.getSegments(entity.id)
        if (segments.isEmpty() || !targetFile.exists()) {
            // Pre-allocate file size
            RandomAccessFile(targetFile, "rw").use { raf ->
                raf.setLength(totalBytes)
            }
            val count = entity.threadCount.coerceIn(2, 16)
            val chunkSize = totalBytes / count
            val created = (0 until count).map { idx ->
                val start = idx * chunkSize
                val end = if (idx == count - 1) totalBytes - 1L else (start + chunkSize - 1L)
                DownloadSegmentEntity(
                    downloadId = entity.id,
                    segmentIndex = idx,
                    startByte = start,
                    endByte = end,
                    downloadedBytes = 0L,
                    isCompleted = false
                )
            }
            repository.replaceSegments(entity.id, created)
            segments = repository.getSegments(entity.id)
        }

        val initialDownloaded = segments.sumOf { it.downloadedBytes }
        val totalCounter = AtomicLong(initialDownloaded)
        liveByteCounters[entity.id] = totalCounter
        lastSampledBytes[entity.id] = initialDownloaded

        val pendingSegments = segments.filter { !it.isCompleted }
        activeThreadCounts[entity.id] = pendingSegments.size

        val client = buildHttpClient()
        val effectiveLimitKbps = resolveEffectiveSpeedLimitKbps(entity)
        val perSegmentLimitBytesPerSec = if (effectiveLimitKbps > 0 && pendingSegments.isNotEmpty()) {
            ((effectiveLimitKbps * 1024L) / pendingSegments.size).coerceAtLeast(8 * 1024L)
        } else {
            0L
        }

        val workers = pendingSegments.map { segment ->
            async(Dispatchers.IO) {
                downloadSegmentWorker(
                    client = client,
                    entity = entity,
                    targetFile = targetFile,
                    segment = segment,
                    totalCounter = totalCounter,
                    segmentSpeedLimitBps = perSegmentLimitBytesPerSec
                )
            }
        }
        workers.awaitAll()
        repository.updateProgress(entity.id, totalBytes, totalBytes)
    }

    private suspend fun downloadSegmentWorker(
        client: OkHttpClient,
        entity: DownloadEntity,
        targetFile: File,
        segment: DownloadSegmentEntity,
        totalCounter: AtomicLong,
        segmentSpeedLimitBps: Long
    ) {
        val resumeOffset = segment.startByte + segment.downloadedBytes
        if (resumeOffset > segment.endByte) {
            repository.updateSegmentProgress(segment.id, segment.totalLength, true)
            return
        }

        val req = buildRequest(
            url = entity.url,
            referer = entity.referer,
            customUserAgent = entity.customUserAgent,
            authHeader = entity.authHeader,
            rangeHeader = "bytes=$resumeOffset-${segment.endByte}"
        )

        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                throw IllegalStateException("خطای سرور در بخش ${segment.segmentIndex + 1}: HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("پاسخ سرور فاقد بدنه داده است.")
            val input = body.byteStream()
            val buffer = ByteArray(16 * 1024)
            var segmentDownloaded = segment.downloadedBytes
            var lastDbSyncTime = System.currentTimeMillis()
            var windowStart = System.currentTimeMillis()
            var windowBytes = 0L

            RandomAccessFile(targetFile, "rw").use { raf ->
                raf.seek(resumeOffset)
                while (coroutineScopeActive()) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    raf.write(buffer, 0, read)
                    segmentDownloaded += read
                    totalCounter.addAndGet(read.toLong())
                    windowBytes += read

                    // Bandwidth Throttling
                    if (segmentSpeedLimitBps > 0L) {
                        val elapsedMs = (System.currentTimeMillis() - windowStart).coerceAtLeast(1L)
                        val expectedMs = (windowBytes * 1000L) / segmentSpeedLimitBps
                        if (expectedMs > elapsedMs) {
                            delay((expectedMs - elapsedMs).coerceAtMost(500L))
                        }
                        if (elapsedMs >= 1000L) {
                            windowStart = System.currentTimeMillis()
                            windowBytes = 0L
                        }
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastDbSyncTime >= 850L) {
                        repository.updateSegmentProgress(segment.id, segmentDownloaded, false)
                        repository.updateProgress(entity.id, totalCounter.get(), entity.totalBytes)
                        lastDbSyncTime = now
                    }
                }
            }
            val done = segmentDownloaded >= segment.totalLength
            repository.updateSegmentProgress(segment.id, segmentDownloaded, done)
        }
    }

    private suspend fun executeSingleStreamDownload(
        entity: DownloadEntity,
        targetFile: File
    ) {
        val existingBytes = if (entity.supportsResume && targetFile.exists()) targetFile.length() else 0L
        val totalCounter = AtomicLong(existingBytes)
        liveByteCounters[entity.id] = totalCounter
        lastSampledBytes[entity.id] = existingBytes
        activeThreadCounts[entity.id] = 1

        val rangeHeader = if (existingBytes > 0L && entity.supportsResume) "bytes=$existingBytes-" else null
        val client = buildHttpClient()
        val req = buildRequest(
            url = entity.url,
            referer = entity.referer,
            customUserAgent = entity.customUserAgent,
            authHeader = entity.authHeader,
            rangeHeader = rangeHeader
        )

        val effectiveLimitKbps = resolveEffectiveSpeedLimitKbps(entity)
        val limitBps = if (effectiveLimitKbps > 0) effectiveLimitKbps * 1024L else 0L

        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                throw IllegalStateException("خطای HTTP ${response.code} در دریافت فایل")
            }
            val body = response.body ?: throw IllegalStateException("پاسخ سرور فاقد داده است.")
            val isResuming = response.code == 206 && existingBytes > 0L
            val contentLength = body.contentLength()
            val totalSize = when {
                isResuming && contentLength > 0L -> existingBytes + contentLength
                contentLength > 0L -> contentLength
                entity.totalBytes > 0L -> entity.totalBytes
                else -> -1L
            }
            liveTotalSizes[entity.id] = totalSize

            if (!isResuming) {
                totalCounter.set(0L)
                lastSampledBytes[entity.id] = 0L
            }

            // Create single segment entry so the inspector still shows clean progress
            val singleSeg = DownloadSegmentEntity(
                downloadId = entity.id,
                segmentIndex = 0,
                startByte = 0L,
                endByte = (totalSize - 1L).coerceAtLeast(0L),
                downloadedBytes = totalCounter.get(),
                isCompleted = false
            )
            repository.replaceSegments(entity.id, listOf(singleSeg))
            val savedSeg = repository.getSegments(entity.id).firstOrNull()

            val buffer = ByteArray(16 * 1024)
            var lastDbSync = System.currentTimeMillis()
            var windowStart = System.currentTimeMillis()
            var windowBytes = 0L

            RandomAccessFile(targetFile, "rw").use { raf ->
                if (isResuming) {
                    raf.seek(existingBytes)
                } else {
                    raf.setLength(0L)
                    raf.seek(0L)
                }
                val input = body.byteStream()
                while (coroutineScopeActive()) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    raf.write(buffer, 0, read)
                    val currentTotal = totalCounter.addAndGet(read.toLong())
                    windowBytes += read

                    if (limitBps > 0L) {
                        val elapsedMs = (System.currentTimeMillis() - windowStart).coerceAtLeast(1L)
                        val expectedMs = (windowBytes * 1000L) / limitBps
                        if (expectedMs > elapsedMs) {
                            delay((expectedMs - elapsedMs).coerceAtMost(500L))
                        }
                        if (elapsedMs >= 1000L) {
                            windowStart = System.currentTimeMillis()
                            windowBytes = 0L
                        }
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastDbSync >= 750L) {
                        repository.updateProgress(entity.id, currentTotal, totalSize)
                        if (savedSeg != null) {
                            repository.updateSegmentProgress(savedSeg.id, currentTotal, false)
                        }
                        lastDbSync = now
                    }
                }
            }
            if (savedSeg != null) {
                repository.updateSegmentProgress(savedSeg.id, totalCounter.get(), true)
            }
        }
    }

    private suspend fun coroutineScopeActive(): Boolean {
        return kotlinx.coroutines.currentCoroutineContext().isActive
    }

    private fun resolveEffectiveSpeedLimitKbps(entity: DownloadEntity): Int {
        val global = currentSettings.globalSpeedLimitKbps
        val perItem = entity.speedLimitKbps
        return when {
            global > 0 && perItem > 0 -> minOf(global, perItem)
            perItem > 0 -> perItem
            global > 0 -> global / activeJobs.size.coerceAtLeast(1)
            else -> 0
        }
    }

    private fun cleanupLiveCounters(downloadId: Long) {
        liveByteCounters.remove(downloadId)
        liveTotalSizes.remove(downloadId)
        activeThreadCounts.remove(downloadId)
        lastSampledBytes.remove(downloadId)
        _telemetryMap.update { it - downloadId }
    }

    private fun startTelemetryLoop() {
        engineScope.launch {
            while (isActive) {
                delay(500L)
                val snapshot = mutableMapOf<Long, DownloadTelemetry>()
                var totalSpeed = 0L

                liveByteCounters.forEach { (id, counter) ->
                    val currentBytes = counter.get()
                    val prevBytes = lastSampledBytes.put(id, currentBytes) ?: currentBytes
                    val delta = (currentBytes - prevBytes).coerceAtLeast(0L)
                    val speedBps = delta * 2L // sampled every 500ms
                    val totalSize = liveTotalSizes[id] ?: -1L
                    val eta = if (totalSize > currentBytes && speedBps > 0L) {
                        (totalSize - currentBytes) / speedBps
                    } else {
                        -1L
                    }
                    totalSpeed += speedBps
                    snapshot[id] = DownloadTelemetry(
                        downloadId = id,
                        speedBytesPerSec = speedBps,
                        etaSeconds = eta,
                        activeThreads = activeThreadCounts[id] ?: 1,
                        liveDownloadedBytes = currentBytes,
                        liveTotalBytes = totalSize
                    )
                }

                _telemetryMap.value = snapshot
                _aggregateSpeedBytesPerSec.value = totalSpeed
                _bandwidthHistory.update { prev ->
                    (prev.drop(1) + totalSpeed)
                }

                val activeCount = activeJobs.size
                if (activeCount > 0) {
                    foregroundRunning = true
                    DownloadForegroundService.updateOrStart(
                        context = context,
                        activeCount = activeCount,
                        speedText = FormatUtils.formatSpeed(totalSpeed)
                    )
                } else if (foregroundRunning) {
                    foregroundRunning = false
                    DownloadForegroundService.stop(context)
                }
            }
        }
    }

    private fun startSchedulerLoop() {
        engineScope.launch {
            while (isActive) {
                delay(15_000L)
                runCatching { evaluateScheduledQueues() }
            }
        }
    }

    private suspend fun evaluateScheduledQueues() {
        val queues = repository.getAllQueues()
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        for (queue in queues) {
            if (!queue.scheduleEnabled) continue
            val startMin = queue.startHour * 60 + queue.startMinute
            val stopMin = queue.stopHour * 60 + queue.stopMinute
            val withinWindow = if (startMin <= stopMin) {
                currentMinutes in startMin until stopMin
            } else {
                // Overnight window (e.g., 23:00 to 06:00)
                currentMinutes >= startMin || currentMinutes < stopMin
            }

            if (withinWindow && !queue.isRunning) {
                startQueue(queue.id)
            } else if (!withinWindow && queue.isRunning) {
                stopQueue(queue.id)
            }
        }
    }

    private suspend fun checkQueueCompletion(queueId: Long) {
        val queue = repository.getQueueById(queueId) ?: return
        val remaining = repository.getQueueDownloadsByStatuses(
            queueId,
            listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING, DownloadStatus.CONNECTING)
        )
        if (remaining.isEmpty() && queue.postAction == PostQueueAction.STOP_QUEUE.name && !queue.isDefault) {
            repository.setQueueRunning(queueId, false)
        }
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun isBatteryCriticallyLow(): Boolean {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return false
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        if (isCharging || level < 0 || scale <= 0) return false
        val pct = (level * 100) / scale
        return pct < 15
    }

    companion object {
        private const val MIN_MULTI_SEGMENT_BYTES = 512L * 1024L // 512 KB threshold for multi-segment
    }
}
