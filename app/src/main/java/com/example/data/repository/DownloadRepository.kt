package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.local.DownloadDatabase
import com.example.data.model.CategoryEntity
import com.example.data.model.DownloadEntity
import com.example.data.model.DownloadSegmentEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.QueueEntity
import com.example.util.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

data class StorageInfo(
    val usedByAppBytes: Long,
    val freeDeviceBytes: Long,
    val totalDeviceBytes: Long
)

class DownloadRepository(
    private val context: Context,
    private val database: DownloadDatabase
) {
    private val downloadDao = database.downloadDao()
    private val segmentDao = database.segmentDao()
    private val categoryDao = database.categoryDao()
    private val queueDao = database.queueDao()

    val downloadsFlow: Flow<List<DownloadEntity>> = downloadDao.observeAllDownloads()
    val categoriesFlow: Flow<List<CategoryEntity>> = categoryDao.observeAllCategories()
    val queuesFlow: Flow<List<QueueEntity>> = queueDao.observeAllQueues()

    fun observeDownload(id: Long): Flow<DownloadEntity?> = downloadDao.observeDownloadById(id)
    fun observeSegments(downloadId: Long): Flow<List<DownloadSegmentEntity>> =
        segmentDao.observeSegments(downloadId)

    suspend fun ensureDefaultsSeeded() = withContext(Dispatchers.IO) {
        if (categoryDao.getAllCategories().isEmpty()) {
            categoryDao.insertAll(DownloadDatabase.defaultCategories)
        }
        if (queueDao.getAllQueues().isEmpty()) {
            queueDao.insertAll(DownloadDatabase.defaultQueues)
        }
        // Reset any stale active statuses from prior abrupt process death to PAUSED
        val stale = downloadDao.getDownloadsByStatuses(
            listOf(DownloadStatus.DOWNLOADING.name, DownloadStatus.CONNECTING.name)
        )
        stale.forEach { item ->
            downloadDao.updateStatus(item.id, DownloadStatus.PAUSED.name, "")
        }
    }

    suspend fun resolveCategoryForFile(fileName: String, mimeType: String? = null): String =
        withContext(Dispatchers.IO) {
            val ext = FormatUtils.getFileExtension(fileName)
            val categories = categoryDao.getAllCategories()
            if (ext.isNotEmpty()) {
                val matched = categories.firstOrNull { cat ->
                    cat.extensionsCsv.split(",")
                        .map { it.trim().lowercase(Locale.US) }
                        .any { it.isNotEmpty() && it == ext }
                }
                if (matched != null) return@withContext matched.id
            }
            val cleanMime = mimeType?.lowercase(Locale.US).orEmpty()
            return@withContext when {
                cleanMime.startsWith("image/") -> "images"
                cleanMime.startsWith("video/") -> "video"
                cleanMime.startsWith("audio/") -> "audio"
                cleanMime.contains("pdf") || cleanMime.contains("document") ||
                    cleanMime.contains("text/") || cleanMime.contains("sheet") -> "documents"
                cleanMime.contains("zip") || cleanMime.contains("rar") ||
                    cleanMime.contains("tar") || cleanMime.contains("7z") ||
                    cleanMime.contains("compressed") -> "archives"
                else -> "other"
            }
        }

    fun getBaseDownloadsDir(): File {
        val external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val base = external ?: File(context.filesDir, "downloads")
        if (!base.exists()) base.mkdirs()
        return base
    }

    suspend fun allocateLocalFile(fileName: String, categoryId: String): File =
        withContext(Dispatchers.IO) {
            val categories = categoryDao.getAllCategories()
            val subfolder = categories.firstOrNull { it.id == categoryId }?.saveSubfolder ?: "General"
            val dir = File(getBaseDownloadsDir(), subfolder)
            if (!dir.exists()) dir.mkdirs()

            val sanitized = FormatUtils.sanitizeFileName(fileName)
            var candidate = File(dir, sanitized)
            if (!candidate.exists()) return@withContext candidate

            val dotIndex = sanitized.lastIndexOf('.')
            val baseName = if (dotIndex > 0) sanitized.substring(0, dotIndex) else sanitized
            val extension = if (dotIndex > 0) sanitized.substring(dotIndex) else ""
            var counter = 1
            while (candidate.exists()) {
                candidate = File(dir, "${baseName}_($counter)$extension")
                counter++
            }
            candidate
        }

    suspend fun insertDownload(download: DownloadEntity): Long = downloadDao.insertDownload(download)
    suspend fun getDownloadById(id: Long): DownloadEntity? = downloadDao.getDownloadById(id)
    suspend fun updateDownload(download: DownloadEntity) = downloadDao.updateDownload(download)
    suspend fun updateStatus(id: Long, status: DownloadStatus, errorMessage: String = "") =
        downloadDao.updateStatus(id, status.name, errorMessage)

    suspend fun updateProgress(id: Long, downloadedBytes: Long, totalBytes: Long) =
        downloadDao.updateProgress(id, downloadedBytes, totalBytes)

    suspend fun updateUrlAndStatus(id: Long, newUrl: String, status: DownloadStatus) =
        downloadDao.updateUrlAndStatus(id, newUrl, status.name)

    suspend fun getDownloadsByStatuses(statuses: List<DownloadStatus>): List<DownloadEntity> =
        downloadDao.getDownloadsByStatuses(statuses.map { it.name })

    suspend fun getQueueDownloadsByStatuses(queueId: Long, statuses: List<DownloadStatus>): List<DownloadEntity> =
        downloadDao.getQueueDownloadsByStatuses(queueId, statuses.map { it.name })

    suspend fun getSegments(downloadId: Long): List<DownloadSegmentEntity> =
        segmentDao.getSegments(downloadId)

    suspend fun replaceSegments(downloadId: Long, segments: List<DownloadSegmentEntity>) {
        segmentDao.deleteForDownload(downloadId)
        segmentDao.insertAll(segments)
    }

    suspend fun updateSegmentProgress(segmentId: Long, downloadedBytes: Long, isCompleted: Boolean) {
        segmentDao.updateSegmentProgress(segmentId, downloadedBytes, isCompleted)
    }

    suspend fun deleteDownload(download: DownloadEntity, deleteFileFromDisk: Boolean) =
        withContext(Dispatchers.IO) {
            if (deleteFileFromDisk && download.localPath.isNotBlank()) {
                runCatching { File(download.localPath).delete() }
            }
            segmentDao.deleteForDownload(download.id)
            downloadDao.deleteById(download.id)
        }

    suspend fun clearCompleted() = downloadDao.deleteByStatus(DownloadStatus.COMPLETED.name)

    suspend fun addCustomCategory(nameFa: String, extensionsCsv: String, subfolder: String) {
        val cleanId = "custom_${System.currentTimeMillis()}"
        val cleanExtensions = extensionsCsv
            .split(",", " ", "،", ";")
            .map { it.trim().trimStart('.').lowercase(Locale.US) }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(",")
        val safeSubfolder = FormatUtils.sanitizeFileName(subfolder.ifBlank { nameFa })
        categoryDao.insert(
            CategoryEntity(
                id = cleanId,
                nameFa = nameFa.trim(),
                extensionsCsv = cleanExtensions,
                iconKey = "custom",
                saveSubfolder = safeSubfolder,
                isCustom = true
            )
        )
    }

    suspend fun deleteCustomCategory(categoryId: String) = categoryDao.deleteCustomCategory(categoryId)

    suspend fun getAllQueues(): List<QueueEntity> = queueDao.getAllQueues()
    suspend fun getQueueById(id: Long): QueueEntity? = queueDao.getQueueById(id)
    suspend fun saveQueue(queue: QueueEntity): Long = queueDao.insert(queue)
    suspend fun updateQueue(queue: QueueEntity) = queueDao.update(queue)
    suspend fun setQueueRunning(id: Long, running: Boolean) = queueDao.setRunning(id, running)
    suspend fun deleteCustomQueue(id: Long) = queueDao.deleteCustomQueue(id)

    suspend fun calculateChecksums(download: DownloadEntity): Pair<String, String> =
        withContext(Dispatchers.IO) {
            val file = File(download.localPath)
            require(file.exists() && file.isFile) { "فایل در مسیر ذخیره‌سازی یافت نشد." }
            val sha256Digest = MessageDigest.getInstance("SHA-256")
            val md5Digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(64 * 1024)
            FileInputStream(file).use { fis ->
                while (true) {
                    val read = fis.read(buffer)
                    if (read <= 0) break
                    sha256Digest.update(buffer, 0, read)
                    md5Digest.update(buffer, 0, read)
                }
            }
            val shaHex = sha256Digest.digest().joinToString("") { "%02x".format(it) }
            val md5Hex = md5Digest.digest().joinToString("") { "%02x".format(it) }
            downloadDao.updateChecksums(download.id, shaHex, md5Hex)
            shaHex to md5Hex
        }

    suspend fun exportToPublicDownloads(download: DownloadEntity): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = File(download.localPath)
                require(source.exists() && source.isFile) { "فایل برای انتقال یافت نشد." }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, download.fileName)
                        put(
                            MediaStore.Downloads.MIME_TYPE,
                            download.mimeType.ifBlank { FormatUtils.guessMimeType(download.fileName) }
                        )
                        put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/PDM")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: error("خطا در ذخیره فایل در پوشه دانلودها")
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(source).use { input ->
                            input.copyTo(out, 64 * 1024)
                        }
                    } ?: error("خطا در نوشتن فایل")
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    downloadDao.updateExportedFlag(download.id, true)
                    "Downloads/PDM/${download.fileName}"
                } else {
                    downloadDao.updateExportedFlag(download.id, true)
                    source.absolutePath
                }
            }
        }

    fun getFileProviderUri(download: DownloadEntity): Uri? {
        if (download.localPath.isBlank()) return null
        val file = File(download.localPath)
        if (!file.exists()) return null
        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    fun createOpenFileIntent(download: DownloadEntity): Intent? {
        val uri = getFileProviderUri(download) ?: return null
        val mime = download.mimeType.ifBlank { FormatUtils.guessMimeType(download.fileName) }
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createShareFileIntent(download: DownloadEntity): Intent? {
        val uri = getFileProviderUri(download) ?: return null
        val mime = download.mimeType.ifBlank { "*/*" }
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
        val baseDir = getBaseDownloadsDir()
        val appUsed = baseDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val stat = runCatching { StatFs(baseDir.absolutePath) }.getOrNull()
        val freeBytes = stat?.availableBytes ?: 0L
        val totalBytes = stat?.totalBytes ?: 0L
        StorageInfo(
            usedByAppBytes = appUsed,
            freeDeviceBytes = freeBytes,
            totalDeviceBytes = totalBytes
        )
    }

    suspend fun cleanOrphanedFiles(): Int = withContext(Dispatchers.IO) {
        val activePaths = downloadDao.getDownloadsByStatuses(DownloadStatus.entries.map { it.name })
            .mapNotNull { it.localPath.takeIf(String::isNotBlank) }
            .toSet()
        var deletedCount = 0
        getBaseDownloadsDir().walkTopDown().filter { it.isFile }.forEach { file ->
            if (file.absolutePath !in activePaths) {
                if (file.delete()) deletedCount++
            }
        }
        deletedCount
    }
}
