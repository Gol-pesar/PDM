package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.CategoryEntity
import com.example.data.model.DownloadEntity
import com.example.data.model.DownloadSegmentEntity
import com.example.data.model.QueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    fun observeDownloadById(id: Long): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY priority DESC, createdAt DESC")
    fun observeDownloadsByStatuses(statuses: List<String>): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY completedAt DESC, createdAt DESC")
    fun observeFinishedDownloads(status: String): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY priority DESC, createdAt ASC")
    suspend fun getDownloadsByStatuses(statuses: List<String>): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE queueId = :queueId AND status IN (:statuses) ORDER BY priority DESC, createdAt ASC")
    suspend fun getQueueDownloadsByStatuses(queueId: Long, statuses: List<String>): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity): Long

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, errorMessage: String = "")

    @Query("UPDATE downloads SET status = :targetStatus, errorMessage = '' WHERE status IN (:sourceStatuses)")
    suspend fun updateStatuses(sourceStatuses: List<String>, targetStatus: String)

    @Query("UPDATE downloads SET status = :targetStatus, errorMessage = '' WHERE queueId = :queueId AND status IN (:sourceStatuses)")
    suspend fun updateQueueStatuses(queueId: Long, sourceStatuses: List<String>, targetStatus: String)

    @Query("UPDATE downloads SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: Long, priority: Int)

    @Query("UPDATE downloads SET queueId = :queueId WHERE id = :id")
    suspend fun updateQueueAssignment(id: Long, queueId: Long)

    @Query("UPDATE downloads SET status = :status, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, completedAt = :completedAt, errorMessage = '' WHERE id = :id")
    suspend fun markCompleted(id: Long, status: String, downloadedBytes: Long, totalBytes: Long, completedAt: Long)

    @Query("UPDATE downloads SET downloadedBytes = :downloadedBytes, totalBytes = :totalBytes WHERE id = :id")
    suspend fun updateProgress(id: Long, downloadedBytes: Long, totalBytes: Long)

    @Query("UPDATE downloads SET url = :newUrl, status = :status, errorMessage = '' WHERE id = :id")
    suspend fun updateUrlAndStatus(id: Long, newUrl: String, status: String)

    @Query("UPDATE downloads SET sha256 = :sha256, md5 = :md5 WHERE id = :id")
    suspend fun updateChecksums(id: Long, sha256: String, md5: String)

    @Query("UPDATE downloads SET exportedToPublic = :exported WHERE id = :id")
    suspend fun updateExportedFlag(id: Long, exported: Boolean)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads WHERE status = :status")
    suspend fun deleteByStatus(status: String)

    @Query("DELETE FROM downloads WHERE queueId = :queueId AND status = :status")
    suspend fun deleteQueueByStatus(queueId: Long, status: String)
}

@Dao
interface SegmentDao {
    @Query("SELECT * FROM download_segments WHERE downloadId = :downloadId ORDER BY segmentIndex ASC")
    fun observeSegments(downloadId: Long): Flow<List<DownloadSegmentEntity>>

    @Query("SELECT * FROM download_segments WHERE downloadId = :downloadId ORDER BY segmentIndex ASC")
    suspend fun getSegments(downloadId: Long): List<DownloadSegmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<DownloadSegmentEntity>)

    @Query("UPDATE download_segments SET downloadedBytes = :downloadedBytes, isCompleted = :isCompleted WHERE id = :id")
    suspend fun updateSegmentProgress(id: Long, downloadedBytes: Long, isCompleted: Boolean)

    @Query("DELETE FROM download_segments WHERE downloadId = :downloadId")
    suspend fun deleteForDownload(downloadId: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isCustom ASC, nameFa ASC")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustomCategory(id: String)
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM queues ORDER BY isDefault DESC, id ASC")
    fun observeAllQueues(): Flow<List<QueueEntity>>

    @Query("SELECT * FROM queues ORDER BY id ASC")
    suspend fun getAllQueues(): List<QueueEntity>

    @Query("SELECT * FROM queues WHERE id = :id LIMIT 1")
    suspend fun getQueueById(id: Long): QueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(queue: QueueEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(queues: List<QueueEntity>)

    @Update
    suspend fun update(queue: QueueEntity)

    @Query("UPDATE queues SET isRunning = :isRunning WHERE id = :id")
    suspend fun setRunning(id: Long, isRunning: Boolean)

    @Query("DELETE FROM queues WHERE id = :id AND isDefault = 0")
    suspend fun deleteCustomQueue(id: Long)
}
