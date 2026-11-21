package com.example.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DownloadDatabase
import com.example.data.model.DownloadEntity
import com.example.data.model.DownloadPriority
import com.example.data.model.DownloadStatus
import com.example.data.model.QueueEntity
import com.example.data.prefs.SettingsRepository
import com.example.data.repository.DownloadRepository
import com.example.engine.DownloadEngine
import com.example.util.FormatUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class QueueBucket(val labelFa: String) {
    ACTIVE("فعال"),
    PAUSED("متوقف"),
    FINISHED("تکمیل‌شده")
}

sealed interface DownloadQueueUiState {
    data object Loading : DownloadQueueUiState

    data class Success(
        val activeTasks: List<DownloadEntity> = emptyList(),
        val pausedTasks: List<DownloadEntity> = emptyList(),
        val finishedTasks: List<DownloadEntity> = emptyList(),
        val failedTasks: List<DownloadEntity> = emptyList(),
        val queues: List<QueueEntity> = emptyList(),
        val selectedQueueId: Long? = null,
        val selectedBucket: QueueBucket = QueueBucket.ACTIVE
    ) : DownloadQueueUiState {
        val activeCount: Int get() = activeTasks.size
        val pausedCount: Int get() = pausedTasks.size
        val finishedCount: Int get() = finishedTasks.size
        val failedCount: Int get() = failedTasks.size
        val totalCount: Int get() = activeCount + pausedCount + finishedCount + failedCount

        val currentBucketTasks: List<DownloadEntity>
            get() = when (selectedBucket) {
                QueueBucket.ACTIVE -> activeTasks
                QueueBucket.PAUSED -> pausedTasks
                QueueBucket.FINISHED -> finishedTasks
            }
    }

    data class Error(val message: String) : DownloadQueueUiState
}

/**
 * Manages active, paused, and finished download tasks with Room persistence
 * through [DownloadRepository] and optional live execution via [DownloadEngine].
 */
class DownloadQueueViewModel(
    private val repository: DownloadRepository,
    private val engine: DownloadEngine? = null
) : ViewModel() {

    private val _selectedQueueId = MutableStateFlow<Long?>(null)
    val selectedQueueId: StateFlow<Long?> = _selectedQueueId.asStateFlow()

    private val _selectedBucket = MutableStateFlow(QueueBucket.ACTIVE)
    val selectedBucket: StateFlow<QueueBucket> = _selectedBucket.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val bucketedTasksFlow = combine(
        repository.activeDownloadsFlow,
        repository.pausedDownloadsFlow,
        repository.finishedDownloadsFlow,
        repository.failedDownloadsFlow,
        repository.queuesFlow
    ) { active, paused, finished, failed, queues ->
        QueueDataSnapshot(
            active = active,
            paused = paused,
            finished = finished,
            failed = failed,
            queues = queues
        )
    }

    val uiState: StateFlow<DownloadQueueUiState> = combine(
        bucketedTasksFlow,
        _selectedQueueId,
        _selectedBucket
    ) { snapshot, queueFilter, bucket ->
        val filteredActive = filterByQueue(snapshot.active, queueFilter)
        val filteredPaused = filterByQueue(snapshot.paused, queueFilter)
        val filteredFinished = filterByQueue(snapshot.finished, queueFilter)
        val filteredFailed = filterByQueue(snapshot.failed, queueFilter)

        DownloadQueueUiState.Success(
            activeTasks = filteredActive,
            pausedTasks = filteredPaused,
            finishedTasks = filteredFinished,
            failedTasks = filteredFailed,
            queues = snapshot.queues,
            selectedQueueId = queueFilter,
            selectedBucket = bucket
        ) as DownloadQueueUiState
    }
        .catch { err ->
            emit(DownloadQueueUiState.Error(err.localizedMessage ?: "خطا در بارگذاری صف دانلودها"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DownloadQueueUiState.Loading
        )

    val activeTasks: StateFlow<List<DownloadEntity>> = uiState
        .map { (it as? DownloadQueueUiState.Success)?.activeTasks.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pausedTasks: StateFlow<List<DownloadEntity>> = uiState
        .map { (it as? DownloadQueueUiState.Success)?.pausedTasks.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val finishedTasks: StateFlow<List<DownloadEntity>> = uiState
        .map { (it as? DownloadQueueUiState.Success)?.finishedTasks.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            runCatching { repository.ensureDefaultsSeeded() }
        }
    }

    fun selectBucket(bucket: QueueBucket) {
        _selectedBucket.value = bucket
    }

    fun selectQueue(queueId: Long?) {
        _selectedQueueId.value = queueId
    }

    fun enqueueTask(
        url: String,
        fileName: String = "",
        categoryId: String = "",
        queueId: Long = _selectedQueueId.value ?: 1L,
        priority: DownloadPriority = DownloadPriority.NORMAL,
        threadCount: Int = 8,
        speedLimitKbps: Int = 0,
        startImmediately: Boolean = true,
        totalBytes: Long = -1L,
        mimeType: String = ""
    ) {
        viewModelScope.launch {
            val trimmedUrl = url.trim()
            if (!FormatUtils.isValidHttpUrl(trimmedUrl)) {
                _events.emit("نشانی لینک نامعتبر است.")
                return@launch
            }
            val resolvedName = FormatUtils.sanitizeFileName(
                fileName.ifBlank { FormatUtils.extractFileNameFromUrl(trimmedUrl, null, mimeType) }
            )
            val resolvedCategory = categoryId.ifBlank {
                repository.resolveCategoryForFile(resolvedName, mimeType)
            }
            val resolvedMime = mimeType.ifBlank { FormatUtils.guessMimeType(resolvedName) }
            val entity = DownloadEntity(
                url = trimmedUrl,
                fileName = resolvedName,
                categoryId = resolvedCategory,
                queueId = queueId,
                mimeType = resolvedMime,
                totalBytes = totalBytes,
                status = if (startImmediately) DownloadStatus.QUEUED.name else DownloadStatus.PAUSED.name,
                priority = priority.level,
                threadCount = threadCount.coerceIn(1, 16),
                speedLimitKbps = speedLimitKbps.coerceAtLeast(0)
            )
            val insertedId = repository.insertDownload(entity)
            if (startImmediately) {
                engine?.startOrResumeDownload(insertedId)
                _events.emit("دانلود «$resolvedName» به صف فعال اضافه شد.")
            } else {
                _events.emit("«$resolvedName» در لیست متوقف ذخیره شد.")
            }
        }
    }

    fun pauseTask(downloadId: Long) {
        viewModelScope.launch {
            if (engine != null) {
                engine.pauseDownload(downloadId)
            } else {
                repository.updateStatus(downloadId, DownloadStatus.PAUSED)
            }
        }
    }

    fun resumeTask(downloadId: Long) {
        viewModelScope.launch {
            if (engine != null) {
                engine.startOrResumeDownload(downloadId)
            } else {
                repository.updateStatus(downloadId, DownloadStatus.QUEUED)
            }
        }
    }

    fun retryFailedTask(downloadId: Long) {
        resumeTask(downloadId)
    }

    fun markTaskDownloading(downloadId: Long) {
        viewModelScope.launch {
            repository.updateStatus(downloadId, DownloadStatus.DOWNLOADING)
        }
    }

    fun updateTaskProgress(downloadId: Long, downloadedBytes: Long, totalBytes: Long) {
        viewModelScope.launch {
            repository.updateProgress(
                id = downloadId,
                downloadedBytes = downloadedBytes.coerceAtLeast(0L),
                totalBytes = totalBytes
            )
        }
    }

    fun markTaskFinished(downloadId: Long) {
        viewModelScope.launch {
            repository.markDownloadCompleted(downloadId)
        }
    }

    fun markTaskFailed(downloadId: Long, errorMessage: String) {
        viewModelScope.launch {
            repository.updateStatus(downloadId, DownloadStatus.FAILED, errorMessage)
        }
    }

    fun pauseAllActive() {
        viewModelScope.launch {
            val queueFilter = _selectedQueueId.value
            if (engine != null && queueFilter == null) {
                engine.pauseAll()
            } else if (engine != null && queueFilter != null) {
                engine.stopQueue(queueFilter)
            } else {
                repository.updateStatuses(
                    sourceStatuses = ACTIVE_STATUSES,
                    targetStatus = DownloadStatus.PAUSED,
                    queueId = queueFilter
                )
            }
            _events.emit("تمام دانلودهای فعال متوقف شدند.")
        }
    }

    fun resumeAllPaused() {
        viewModelScope.launch {
            val queueFilter = _selectedQueueId.value
            if (engine != null && queueFilter == null) {
                engine.resumeAll()
            } else if (engine != null && queueFilter != null) {
                engine.startQueue(queueFilter)
            } else {
                repository.updateStatuses(
                    sourceStatuses = listOf(DownloadStatus.PAUSED, DownloadStatus.FAILED),
                    targetStatus = DownloadStatus.QUEUED,
                    queueId = queueFilter
                )
            }
            _events.emit("تمام دانلودهای متوقف در صف فعال قرار گرفتند.")
        }
    }

    fun removeTask(downloadId: Long, deleteFileFromDisk: Boolean = false) {
        viewModelScope.launch {
            if (engine != null) {
                engine.cancelDownload(downloadId, deleteFileFromDisk)
            } else {
                repository.deleteDownloadById(downloadId, deleteFileFromDisk)
            }
            _events.emit("مورد از صف دانلود حذف شد.")
        }
    }

    fun clearFinishedTasks() {
        viewModelScope.launch {
            repository.clearCompleted(_selectedQueueId.value)
            _events.emit("فهرست دانلودهای تکمیل‌شده پاکسازی شد.")
        }
    }

    fun updateTaskPriority(downloadId: Long, priority: DownloadPriority) {
        viewModelScope.launch {
            repository.updatePriority(downloadId, priority.level)
        }
    }

    fun moveTaskToQueue(downloadId: Long, targetQueueId: Long) {
        viewModelScope.launch {
            repository.updateQueueAssignment(downloadId, targetQueueId)
        }
    }

    private fun filterByQueue(
        tasks: List<DownloadEntity>,
        queueId: Long?
    ): List<DownloadEntity> = if (queueId == null) tasks else tasks.filter { it.queueId == queueId }

    private data class QueueDataSnapshot(
        val active: List<DownloadEntity>,
        val paused: List<DownloadEntity>,
        val finished: List<DownloadEntity>,
        val failed: List<DownloadEntity>,
        val queues: List<QueueEntity>
    )

    companion object {
        private val ACTIVE_STATUSES = listOf(
            DownloadStatus.DOWNLOADING,
            DownloadStatus.CONNECTING,
            DownloadStatus.QUEUED
        )

        fun factory(
            repository: DownloadRepository,
            engine: DownloadEngine? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(DownloadQueueViewModel::class.java)) {
                    return DownloadQueueViewModel(repository, engine) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }

        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(DownloadQueueViewModel::class.java)) {
                        val db = DownloadDatabase.getInstance(application)
                        val repo = DownloadRepository(application, db)
                        val settingsRepo = SettingsRepository(application)
                        val downloadEngine = DownloadEngine(application, repo, settingsRepo)
                        return DownloadQueueViewModel(repo, downloadEngine) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
    }
}
