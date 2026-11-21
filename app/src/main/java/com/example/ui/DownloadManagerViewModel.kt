package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DownloadDatabase
import com.example.data.model.AppSettings
import com.example.data.model.CategoryEntity
import com.example.data.model.CategorySummary
import com.example.data.model.ChecksumVerificationState
import com.example.data.model.DownloadEntity
import com.example.data.model.DownloadPriority
import com.example.data.model.DownloadSegmentEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTelemetry
import com.example.data.model.PostQueueAction
import com.example.data.model.QueueEntity
import com.example.data.model.SortOption
import com.example.data.model.StatusFilter
import com.example.data.model.ThemePreference
import com.example.data.model.UrlProbeResult
import com.example.data.prefs.SettingsRepository
import com.example.data.repository.DownloadRepository
import com.example.data.repository.StorageInfo
import com.example.engine.DownloadEngine
import com.example.util.FormatUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab(val labelFa: String) {
    TRANSFERS("دانلودها"),
    CATEGORIES("دسته‌بندی‌ها"),
    SETTINGS("تنظیمات")
}

data class FilterState(
    val searchQuery: String = "",
    val statusFilter: StatusFilter = StatusFilter.ALL,
    val selectedCategoryId: String? = null,
    val sortOption: SortOption = SortOption.NEWEST
)

data class ProbeUiState(
    val isProbing: Boolean = false,
    val result: UrlProbeResult? = null
)

data class MainUiState(
    val allDownloads: List<DownloadEntity> = emptyList(),
    val filteredDownloads: List<DownloadEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val categorySummaries: List<CategorySummary> = emptyList(),
    val queues: List<QueueEntity> = emptyList(),
    val telemetry: Map<Long, DownloadTelemetry> = emptyMap(),
    val aggregateSpeedBps: Long = 0L,
    val bandwidthHistory: List<Long> = List(36) { 0L },
    val filterState: FilterState = FilterState(),
    val settings: AppSettings = AppSettings(),
    val storageInfo: StorageInfo = StorageInfo(0L, 0L, 0L),
    val activeCount: Int = 0,
    val queuedCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0
)

class DownloadManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = DownloadDatabase.getInstance(application)
    val repository = DownloadRepository(application, database)
    private val settingsRepository = SettingsRepository(application)
    private val engine = DownloadEngine(application, repository, settingsRepository)

    private val _filterState = MutableStateFlow(FilterState())
    private val _storageInfo = MutableStateFlow(StorageInfo(0L, 0L, 0L))

    private val _probeState = MutableStateFlow(ProbeUiState())
    val probeState: StateFlow<ProbeUiState> = _probeState.asStateFlow()

    private val _inspectedDownloadId = MutableStateFlow<Long?>(null)
    val inspectedDownloadId: StateFlow<Long?> = _inspectedDownloadId.asStateFlow()

    private val _checksumState = MutableStateFlow<ChecksumVerificationState?>(null)
    val checksumState: StateFlow<ChecksumVerificationState?> = _checksumState.asStateFlow()

    private val _pendingSharedUrl = MutableStateFlow<String?>(null)
    val pendingSharedUrl: StateFlow<String?> = _pendingSharedUrl.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val inspectedDownload: StateFlow<DownloadEntity?> = _inspectedDownloadId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.observeDownload(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val inspectedSegments: StateFlow<List<DownloadSegmentEntity>> = _inspectedDownloadId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeSegments(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val coreDataFlow = combine(
        repository.downloadsFlow,
        repository.categoriesFlow,
        repository.queuesFlow,
        settingsRepository.settingsFlow
    ) { downloads, categories, queues, settings ->
        CoreBundle(downloads, categories, queues, settings)
    }

    private val liveStatsFlow = combine(
        engine.telemetryMap,
        engine.aggregateSpeedBytesPerSec,
        engine.bandwidthHistory,
        _storageInfo
    ) { telemetry, aggSpeed, history, storage ->
        LiveBundle(telemetry, aggSpeed, history, storage)
    }

    val uiState: StateFlow<MainUiState> = combine(
        coreDataFlow,
        liveStatsFlow,
        _filterState
    ) { core, live, filter ->
        val filtered = applyFilters(core.downloads, filter)
        val summaries = core.categories.map { cat ->
            val matching = core.downloads.filter { it.categoryId == cat.id }
            CategorySummary(
                category = cat,
                itemCount = matching.size,
                totalBytes = matching.sumOf {
                    if (it.totalBytes > 0L) it.totalBytes else it.downloadedBytes
                },
                completedCount = matching.count { it.statusEnum == DownloadStatus.COMPLETED }
            )
        }
        MainUiState(
            allDownloads = core.downloads,
            filteredDownloads = filtered,
            categories = core.categories,
            categorySummaries = summaries,
            queues = core.queues,
            telemetry = live.telemetry,
            aggregateSpeedBps = live.aggSpeed,
            bandwidthHistory = live.history,
            filterState = filter,
            settings = core.settings,
            storageInfo = live.storage,
            activeCount = core.downloads.count {
                it.statusEnum == DownloadStatus.DOWNLOADING || it.statusEnum == DownloadStatus.CONNECTING
            },
            queuedCount = core.downloads.count { it.statusEnum == DownloadStatus.QUEUED },
            completedCount = core.downloads.count { it.statusEnum == DownloadStatus.COMPLETED },
            failedCount = core.downloads.count { it.statusEnum == DownloadStatus.FAILED }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    init {
        viewModelScope.launch {
            repository.ensureDefaultsSeeded()
            refreshStorageInfo()
        }
    }

    fun refreshStorageInfo() {
        viewModelScope.launch {
            _storageInfo.value = repository.getStorageInfo()
        }
    }

    fun onIncomingSharedText(sharedText: String?) {
        if (sharedText.isNullOrBlank()) return
        val urlRegex = Regex("https?://\\S+")
        val extracted = urlRegex.find(sharedText)?.value ?: sharedText.trim()
        if (FormatUtils.isValidHttpUrl(extracted)) {
            _pendingSharedUrl.value = extracted
        }
    }

    fun consumePendingSharedUrl() {
        _pendingSharedUrl.value = null
    }

    fun setSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun setStatusFilter(filter: StatusFilter) {
        _filterState.value = _filterState.value.copy(statusFilter = filter)
    }

    fun setCategoryFilter(categoryId: String?) {
        _filterState.value = _filterState.value.copy(selectedCategoryId = categoryId)
    }

    fun setSortOption(option: SortOption) {
        _filterState.value = _filterState.value.copy(sortOption = option)
    }

    fun clearProbe() {
        _probeState.value = ProbeUiState()
    }

    fun probeUrl(
        url: String,
        referer: String = "",
        userAgent: String = "",
        authHeader: String = ""
    ) {
        viewModelScope.launch {
            _probeState.value = ProbeUiState(isProbing = true)
            val result = engine.probeUrl(url, referer, userAgent, authHeader)
            _probeState.value = ProbeUiState(isProbing = false, result = result)
        }
    }

    fun enqueueSingleDownload(
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
        probedSize: Long = -1L,
        probedMime: String = "",
        probedSupportsResume: Boolean = false,
        probedEtag: String = ""
    ) {
        viewModelScope.launch {
            val trimmedUrl = url.trim()
            if (!FormatUtils.isValidHttpUrl(trimmedUrl)) {
                _toastEvents.emit("نشانی لینک نامعتبر است.")
                return@launch
            }
            val finalName = FormatUtils.sanitizeFileName(
                fileName.ifBlank { FormatUtils.extractFileNameFromUrl(trimmedUrl, null, probedMime) }
            )
            val resolvedCategory = categoryId.ifBlank {
                repository.resolveCategoryForFile(finalName, probedMime)
            }
            val mime = probedMime.ifBlank { FormatUtils.guessMimeType(finalName) }
            val entity = DownloadEntity(
                url = trimmedUrl,
                fileName = finalName,
                categoryId = resolvedCategory,
                queueId = queueId,
                mimeType = mime,
                totalBytes = probedSize,
                status = if (startImmediately) DownloadStatus.QUEUED.name else DownloadStatus.PAUSED.name,
                priority = priority.level,
                threadCount = threadCount.coerceIn(1, 16),
                supportsResume = probedSupportsResume,
                etag = probedEtag,
                referer = referer.trim(),
                customUserAgent = customUserAgent.trim(),
                authHeader = authHeader.trim(),
                speedLimitKbps = speedLimitKbps.coerceAtLeast(0)
            )
            val newId = repository.insertDownload(entity)
            if (startImmediately) {
                engine.startOrResumeDownload(newId)
                _toastEvents.emit("دریافت «$finalName» آغاز شد.")
            } else {
                _toastEvents.emit("«$finalName» در صف ذخیره شد.")
            }
            refreshStorageInfo()
        }
    }

    fun enqueueBatchUrls(
        rawLines: String,
        queueId: Long,
        priority: DownloadPriority,
        threadCount: Int,
        startImmediately: Boolean
    ) {
        viewModelScope.launch {
            val urls = rawLines.lines()
                .map { it.trim() }
                .filter { FormatUtils.isValidHttpUrl(it) }
                .distinct()

            if (urls.isEmpty()) {
                _toastEvents.emit("هیچ لینک معتبری در متن ورودی یافت نشد.")
                return@launch
            }

            urls.forEach { link ->
                val name = FormatUtils.extractFileNameFromUrl(link)
                val catId = repository.resolveCategoryForFile(name)
                val entity = DownloadEntity(
                    url = link,
                    fileName = name,
                    categoryId = catId,
                    queueId = queueId,
                    mimeType = FormatUtils.guessMimeType(name),
                    status = if (startImmediately) DownloadStatus.QUEUED.name else DownloadStatus.PAUSED.name,
                    priority = priority.level,
                    threadCount = threadCount.coerceIn(1, 16)
                )
                repository.insertDownload(entity)
            }
            if (startImmediately) {
                engine.dispatchQueuedDownloads()
            }
            _toastEvents.emit("${urls.size} لینک به سامانه اضافه شد.")
            refreshStorageInfo()
        }
    }

    fun startOrResume(downloadId: Long) {
        engine.startOrResumeDownload(downloadId)
    }

    fun pause(downloadId: Long) {
        engine.pauseDownload(downloadId)
    }

    fun pauseAll() {
        engine.pauseAll()
        viewModelScope.launch { _toastEvents.emit("تمام انتقال‌های فعال متوقف شدند.") }
    }

    fun resumeAll() {
        engine.resumeAll()
        viewModelScope.launch { _toastEvents.emit("تمام انتقال‌های متوقف در صف ادامه قرار گرفتند.") }
    }

    fun deleteDownload(downloadId: Long, deleteFile: Boolean) {
        if (_inspectedDownloadId.value == downloadId) {
            _inspectedDownloadId.value = null
        }
        engine.cancelDownload(downloadId, deleteFile)
        viewModelScope.launch {
            _toastEvents.emit(if (deleteFile) "رکورد و فایل از حافظه حذف شد." else "رکورد از فهرست حذف شد.")
            refreshStorageInfo()
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            repository.clearCompleted()
            _toastEvents.emit("فهرست فایل‌های تکمیل‌شده پاکسازی شد.")
            refreshStorageInfo()
        }
    }

    fun openInspector(downloadId: Long) {
        _inspectedDownloadId.value = downloadId
        _checksumState.value = null
    }

    fun closeInspector() {
        _inspectedDownloadId.value = null
        _checksumState.value = null
    }

    fun updateExpiredLink(downloadId: Long, newUrl: String) {
        viewModelScope.launch {
            val trimmed = newUrl.trim()
            if (!FormatUtils.isValidHttpUrl(trimmed)) {
                _toastEvents.emit("لینک جایگزین نامعتبر است.")
                return@launch
            }
            engine.pauseDownload(downloadId)
            repository.updateUrlAndStatus(downloadId, trimmed, DownloadStatus.QUEUED)
            engine.startOrResumeDownload(downloadId)
            _toastEvents.emit("لینک جدید ثبت و ادامه دریافت آغاز شد.")
        }
    }

    fun calculateChecksums(download: DownloadEntity) {
        viewModelScope.launch {
            _checksumState.value = ChecksumVerificationState(
                downloadId = download.id,
                isCalculating = true,
                expectedHash = _checksumState.value?.expectedHash.orEmpty()
            )
            runCatching { repository.calculateChecksums(download) }
                .onSuccess { (sha256, md5) ->
                    val expected = _checksumState.value?.expectedHash?.trim()?.lowercase().orEmpty()
                    val match = if (expected.isEmpty()) {
                        null
                    } else {
                        expected == sha256.lowercase() || expected == md5.lowercase()
                    }
                    _checksumState.value = ChecksumVerificationState(
                        downloadId = download.id,
                        isCalculating = false,
                        sha256 = sha256,
                        md5 = md5,
                        expectedHash = expected,
                        isMatch = match
                    )
                }
                .onFailure { err ->
                    _checksumState.value = ChecksumVerificationState(
                        downloadId = download.id,
                        isCalculating = false,
                        error = err.localizedMessage ?: "خطا در محاسبه امضای دیجیتال فایل"
                    )
                }
        }
    }

    fun verifyAgainstExpectedHash(download: DownloadEntity, expectedHash: String) {
        val cleaned = expectedHash.trim().lowercase()
        val current = _checksumState.value
        val sha = current?.sha256?.ifBlank { download.sha256 } ?: download.sha256
        val md5 = current?.md5?.ifBlank { download.md5 } ?: download.md5
        if (sha.isBlank() && md5.isBlank()) {
            _checksumState.value = ChecksumVerificationState(
                downloadId = download.id,
                expectedHash = cleaned
            )
            calculateChecksums(download)
        } else {
            val match = if (cleaned.isEmpty()) null else (cleaned == sha.lowercase() || cleaned == md5.lowercase())
            _checksumState.value = ChecksumVerificationState(
                downloadId = download.id,
                isCalculating = false,
                sha256 = sha,
                md5 = md5,
                expectedHash = cleaned,
                isMatch = match
            )
        }
    }

    fun exportToPublicDownloads(download: DownloadEntity) {
        viewModelScope.launch {
            repository.exportToPublicDownloads(download)
                .onSuccess { path ->
                    _toastEvents.emit("فایل در مسیر عمومی ذخیره شد: $path")
                }
                .onFailure { err ->
                    _toastEvents.emit(err.localizedMessage ?: "خطا در انتقال به پوشه دانلودها")
                }
        }
    }

    fun createOpenFileIntent(download: DownloadEntity): Intent? =
        repository.createOpenFileIntent(download)

    fun createShareFileIntent(download: DownloadEntity): Intent? =
        repository.createShareFileIntent(download)

    // Categories management
    fun addCustomCategory(nameFa: String, extensionsCsv: String, subfolder: String) {
        viewModelScope.launch {
            if (nameFa.isBlank()) {
                _toastEvents.emit("عنوان دسته‌بندی نمی‌تواند خالی باشد.")
                return@launch
            }
            repository.addCustomCategory(nameFa, extensionsCsv, subfolder)
            _toastEvents.emit("دسته‌بندی «$nameFa» ایجاد شد.")
        }
    }

    fun deleteCustomCategory(categoryId: String) {
        viewModelScope.launch {
            repository.deleteCustomCategory(categoryId)
            if (_filterState.value.selectedCategoryId == categoryId) {
                setCategoryFilter(null)
            }
            _toastEvents.emit("دسته‌بندی سفارشی حذف شد.")
        }
    }

    // Queue & Scheduler management
    fun createQueue(
        nameFa: String,
        maxConcurrent: Int,
        scheduleEnabled: Boolean,
        startHour: Int,
        startMinute: Int,
        stopHour: Int,
        stopMinute: Int,
        postAction: PostQueueAction
    ) {
        viewModelScope.launch {
            if (nameFa.isBlank()) return@launch
            repository.saveQueue(
                QueueEntity(
                    nameFa = nameFa.trim(),
                    maxConcurrent = maxConcurrent.coerceIn(1, 5),
                    scheduleEnabled = scheduleEnabled,
                    startHour = startHour.coerceIn(0, 23),
                    startMinute = startMinute.coerceIn(0, 59),
                    stopHour = stopHour.coerceIn(0, 23),
                    stopMinute = stopMinute.coerceIn(0, 59),
                    isRunning = false,
                    postAction = postAction.name,
                    isDefault = false
                )
            )
            _toastEvents.emit("صف «$nameFa» با موفقیت ثبت شد.")
        }
    }

    fun updateQueue(queue: QueueEntity) {
        viewModelScope.launch {
            repository.updateQueue(queue)
            _toastEvents.emit("تنظیمات صف «${queue.nameFa}» ذخیره شد.")
        }
    }

    fun toggleQueueRunning(queue: QueueEntity) {
        if (queue.isRunning) {
            engine.stopQueue(queue.id)
            viewModelScope.launch { _toastEvents.emit("صف «${queue.nameFa}» متوقف شد.") }
        } else {
            engine.startQueue(queue.id)
            viewModelScope.launch { _toastEvents.emit("اجرای صف «${queue.nameFa}» آغاز شد.") }
        }
    }

    fun deleteCustomQueue(queueId: Long) {
        viewModelScope.launch {
            repository.deleteCustomQueue(queueId)
            _toastEvents.emit("صف سفارشی حذف شد.")
        }
    }

    // Settings mutations
    fun updateTheme(pref: ThemePreference) = viewModelScope.launch { settingsRepository.updateTheme(pref) }
    fun updateDefaultThreads(count: Int) = viewModelScope.launch { settingsRepository.updateDefaultThreads(count) }
    fun updateMaxConcurrent(count: Int) = viewModelScope.launch { settingsRepository.updateMaxConcurrent(count) }
    fun updateGlobalSpeedLimit(kbps: Int) = viewModelScope.launch { settingsRepository.updateGlobalSpeedLimit(kbps) }
    fun updateWifiOnly(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateWifiOnly(enabled) }
    fun updateLowBatteryPause(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateLowBatteryPause(enabled) }
    fun updateAutoRetryCount(count: Int) = viewModelScope.launch { settingsRepository.updateAutoRetryCount(count) }
    fun updateAutoExportPublic(enabled: Boolean) = viewModelScope.launch { settingsRepository.updateAutoExportPublic(enabled) }
    fun updateCustomUserAgent(ua: String) = viewModelScope.launch {
        settingsRepository.updateCustomUserAgent(ua)
        _toastEvents.emit("شناسه User-Agent به‌روزرسانی شد.")
    }

    fun updateProxy(enabled: Boolean, host: String, port: Int) = viewModelScope.launch {
        settingsRepository.updateProxy(enabled, host, port)
        _toastEvents.emit(if (enabled) "پروکسی شبکه فعال شد." else "پروکسی شبکه غیرفعال شد.")
    }

    fun cleanOrphanedFiles() {
        viewModelScope.launch {
            val count = repository.cleanOrphanedFiles()
            refreshStorageInfo()
            _toastEvents.emit(
                if (count > 0) "$count فایل موقت بلااستفاده پاکسازی شد."
                else "فایل موقت اضافه‌ای در حافظه یافت نشد."
            )
        }
    }

    private fun applyFilters(
        downloads: List<DownloadEntity>,
        filter: FilterState
    ): List<DownloadEntity> {
        val query = filter.searchQuery.trim().lowercase()
        val filtered = downloads.filter { item ->
            val matchesSearch = query.isEmpty() ||
                item.fileName.lowercase().contains(query) ||
                item.url.lowercase().contains(query)

            val matchesCategory = filter.selectedCategoryId == null ||
                item.categoryId == filter.selectedCategoryId

            val matchesStatus = when (filter.statusFilter) {
                StatusFilter.ALL -> true
                StatusFilter.ACTIVE -> item.statusEnum == DownloadStatus.DOWNLOADING ||
                    item.statusEnum == DownloadStatus.CONNECTING
                StatusFilter.QUEUED -> item.statusEnum == DownloadStatus.QUEUED
                StatusFilter.PAUSED -> item.statusEnum == DownloadStatus.PAUSED
                StatusFilter.COMPLETED -> item.statusEnum == DownloadStatus.COMPLETED
                StatusFilter.FAILED -> item.statusEnum == DownloadStatus.FAILED
            }

            matchesSearch && matchesCategory && matchesStatus
        }

        return when (filter.sortOption) {
            SortOption.NEWEST -> filtered.sortedByDescending { it.createdAt }
            SortOption.NAME -> filtered.sortedBy { it.fileName.lowercase() }
            SortOption.SIZE -> filtered.sortedByDescending { it.totalBytes }
            SortOption.PROGRESS -> filtered.sortedByDescending { it.progressFraction }
            SortOption.STATUS -> filtered.sortedBy { it.statusEnum.ordinal }
        }
    }

    private data class CoreBundle(
        val downloads: List<DownloadEntity>,
        val categories: List<CategoryEntity>,
        val queues: List<QueueEntity>,
        val settings: AppSettings
    )

    private data class LiveBundle(
        val telemetry: Map<Long, DownloadTelemetry>,
        val aggSpeed: Long,
        val history: List<Long>,
        val storage: StorageInfo
    )
}
