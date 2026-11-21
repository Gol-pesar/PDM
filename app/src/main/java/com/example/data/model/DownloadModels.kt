package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DownloadStatus(val labelFa: String) {
    QUEUED("در صف"),
    CONNECTING("در حال اتصال..."),
    DOWNLOADING("در حال دانلود"),
    PAUSED("متوقف"),
    COMPLETED("تکمیل شد"),
    FAILED("خطا در دانلود"),
    CANCELED("لغو شد")
}

enum class DownloadPriority(val level: Int, val labelFa: String) {
    NORMAL(0, "معمولی"),
    HIGH(1, "مهم"),
    URGENT(2, "فوری")
}

enum class SortOption(val labelFa: String) {
    NEWEST("جدیدترین"),
    NAME("نام فایل"),
    SIZE("حجم فایل"),
    PROGRESS("پیشرفت"),
    STATUS("وضعیت")
}

enum class StatusFilter(val labelFa: String) {
    ALL("همه"),
    ACTIVE("در حال دانلود"),
    QUEUED("در صف"),
    PAUSED("متوقف"),
    COMPLETED("تکمیل‌شده"),
    FAILED("ناموفق")
}

enum class ThemePreference(val labelFa: String) {
    SYSTEM("خودکار"),
    LIGHT("روشن"),
    DARK("تیره")
}

enum class PostQueueAction(val labelFa: String) {
    NONE("بدون کار اضافه"),
    NOTIFY("نمایش اعلان"),
    STOP_QUEUE("توقف خودکار صف")
}

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val nameFa: String,
    val extensionsCsv: String,
    val iconKey: String,
    val saveSubfolder: String,
    val isCustom: Boolean = false
)

@Entity(tableName = "queues")
data class QueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val nameFa: String,
    val maxConcurrent: Int = 2,
    val scheduleEnabled: Boolean = false,
    val startHour: Int = 2,
    val startMinute: Int = 0,
    val stopHour: Int = 7,
    val stopMinute: Int = 0,
    val isRunning: Boolean = false,
    val postAction: String = PostQueueAction.NOTIFY.name,
    val isDefault: Boolean = false
)

@Entity(
    tableName = "downloads",
    indices = [Index("categoryId"), Index("queueId"), Index("status")]
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val url: String,
    val fileName: String,
    val localPath: String = "",
    val categoryId: String = "other",
    val queueId: Long = 1L,
    val mimeType: String = "application/octet-stream",
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val status: String = DownloadStatus.QUEUED.name,
    val priority: Int = DownloadPriority.NORMAL.level,
    val threadCount: Int = 8,
    val supportsResume: Boolean = false,
    val etag: String = "",
    val referer: String = "",
    val customUserAgent: String = "",
    val authHeader: String = "",
    val speedLimitKbps: Int = 0,
    val errorMessage: String = "",
    val sha256: String = "",
    val md5: String = "",
    val exportedToPublic: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val retryCount: Int = 0
) {
    val statusEnum: DownloadStatus
        get() = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.QUEUED)

    val priorityEnum: DownloadPriority
        get() = DownloadPriority.entries.firstOrNull { it.level == priority } ?: DownloadPriority.NORMAL

    val progressFraction: Float
        get() = when {
            statusEnum == DownloadStatus.COMPLETED -> 1f
            totalBytes > 0L -> (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }
}

@Entity(
    tableName = "download_segments",
    foreignKeys = [
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["id"],
            childColumns = ["downloadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("downloadId")]
)
data class DownloadSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val downloadId: Long,
    val segmentIndex: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long = 0L,
    val isCompleted: Boolean = false
) {
    val totalLength: Long
        get() = (endByte - startByte + 1L).coerceAtLeast(1L)

    val progressFraction: Float
        get() = if (isCompleted) 1f else (downloadedBytes.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f)
}

data class DownloadTelemetry(
    val downloadId: Long,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = -1L,
    val activeThreads: Int = 0,
    val liveDownloadedBytes: Long = 0L,
    val liveTotalBytes: Long = -1L
)

data class UrlProbeResult(
    val url: String,
    val fileName: String,
    val contentLength: Long,
    val mimeType: String,
    val supportsResume: Boolean,
    val host: String,
    val suggestedCategoryId: String,
    val etag: String = "",
    val httpCode: Int = 200,
    val error: String? = null
)

data class CategorySummary(
    val category: CategoryEntity,
    val itemCount: Int,
    val totalBytes: Long,
    val completedCount: Int
)

data class AppSettings(
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val defaultThreadCount: Int = 8,
    val maxConcurrentDownloads: Int = 3,
    val globalSpeedLimitKbps: Int = 0,
    val wifiOnly: Boolean = false,
    val lowBatteryPause: Boolean = true,
    val autoRetryCount: Int = 3,
    val autoExportPublicDownloads: Boolean = true,
    val customUserAgent: String = "PDM/1.0 (Palang Download Manager; Android)",
    val proxyEnabled: Boolean = false,
    val proxyHost: String = "",
    val proxyPort: Int = 8080
)

data class ChecksumVerificationState(
    val downloadId: Long,
    val isCalculating: Boolean = false,
    val sha256: String = "",
    val md5: String = "",
    val expectedHash: String = "",
    val isMatch: Boolean? = null,
    val error: String? = null
)
