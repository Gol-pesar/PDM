package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.DownloadDatabase
import com.example.data.model.DownloadPriority
import com.example.data.model.DownloadStatus
import com.example.data.repository.DownloadRepository
import com.example.ui.DownloadQueueUiState
import com.example.ui.DownloadQueueViewModel
import com.example.ui.QueueBucket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DownloadQueueViewModelTest {

    private lateinit var context: Context
    private lateinit var database: DownloadDatabase
    private lateinit var repository: DownloadRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, DownloadDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DownloadRepository(context, database)
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private suspend fun awaitState(
        vm: DownloadQueueViewModel,
        predicate: (DownloadQueueUiState.Success) -> Boolean
    ): DownloadQueueUiState.Success = withTimeout(5_000L) {
        vm.uiState
            .filterIsInstance<DownloadQueueUiState.Success>()
            .first(predicate)
    }

    @Test
    fun `enqueue pause resume and finish transitions persist in Room`() = runBlocking {
        val vm = DownloadQueueViewModel(repository = repository, engine = null)
        val collectJob = launch(Dispatchers.Default) {
            vm.uiState.collect {}
        }

        vm.enqueueTask(
            url = "https://example.com/archive.zip",
            fileName = "archive.zip",
            startImmediately = true,
            totalBytes = 10_000L
        )

        val activeState = awaitState(vm) { it.activeTasks.size == 1 }
        val task = activeState.activeTasks.first()
        assertEquals("archive.zip", task.fileName)
        assertEquals(DownloadStatus.QUEUED, task.statusEnum)
        assertEquals(0, activeState.pausedCount)
        assertEquals(0, activeState.finishedCount)

        // Pause active task
        vm.pauseTask(task.id)
        val pausedState = awaitState(vm) { it.pausedTasks.size == 1 && it.activeTasks.isEmpty() }
        assertEquals(DownloadStatus.PAUSED, pausedState.pausedTasks.first().statusEnum)

        // Resume paused task and mark downloading + progress
        vm.resumeTask(task.id)
        awaitState(vm) { it.activeTasks.size == 1 && it.pausedTasks.isEmpty() }

        vm.markTaskDownloading(task.id)
        vm.updateTaskProgress(task.id, downloadedBytes = 4_000L, totalBytes = 10_000L)
        val downloadingState = awaitState(vm) {
            it.activeTasks.firstOrNull()?.downloadedBytes == 4_000L &&
                it.activeTasks.firstOrNull()?.statusEnum == DownloadStatus.DOWNLOADING
        }
        assertEquals(0.4f, downloadingState.activeTasks.first().progressFraction, 0.01f)

        // Mark task finished
        vm.markTaskFinished(task.id)
        val finishedState = awaitState(vm) { it.finishedTasks.size == 1 && it.activeTasks.isEmpty() }
        val finished = finishedState.finishedTasks.first()
        assertEquals(DownloadStatus.COMPLETED, finished.statusEnum)
        assertEquals(10_000L, finished.downloadedBytes)
        assertTrue(finished.completedAt > 0L)

        // Verify persistence across ViewModel recreation
        val recreatedVm = DownloadQueueViewModel(repository = repository, engine = null)
        val recreateCollect = launch(Dispatchers.Default) {
            recreatedVm.uiState.collect {}
        }
        val restored = awaitState(recreatedVm) { it.finishedTasks.size == 1 }
        assertEquals("archive.zip", restored.finishedTasks.first().fileName)

        recreateCollect.cancel()
        collectJob.cancel()
    }

    @Test
    fun `bulk pause resume clearFinished and queue filtering work accurately`() = runBlocking {
        val vm = DownloadQueueViewModel(repository = repository, engine = null)
        val collectJob = launch(Dispatchers.Default) {
            vm.uiState.collect {}
        }

        vm.enqueueTask(
            url = "https://example.com/file1.pdf",
            fileName = "file1.pdf",
            queueId = 1L,
            priority = DownloadPriority.NORMAL,
            startImmediately = true
        )
        vm.enqueueTask(
            url = "https://example.com/file2.mp4",
            fileName = "file2.mp4",
            queueId = 2L,
            priority = DownloadPriority.URGENT,
            startImmediately = true
        )
        vm.enqueueTask(
            url = "https://example.com/file3.zip",
            fileName = "file3.zip",
            queueId = 1L,
            startImmediately = false
        )

        val initial = awaitState(vm) { it.activeCount == 2 && it.pausedCount == 1 }
        // Higher priority task appears first in activeTasks
        assertEquals("file2.mp4", initial.activeTasks.first().fileName)

        // Filter to queue 1
        vm.selectQueue(1L)
        vm.selectBucket(QueueBucket.PAUSED)
        val q1State = awaitState(vm) {
            it.selectedQueueId == 1L && it.activeCount == 1 && it.pausedCount == 1
        }
        assertEquals(1, q1State.currentBucketTasks.size)
        assertEquals("file3.zip", q1State.currentBucketTasks.first().fileName)

        // Pause all in queue 1 only (queue 2 stays active)
        vm.pauseAllActive()
        awaitState(vm) { it.activeCount == 0 && it.pausedCount == 2 }

        // Clear queue filter and verify queue 2 is still active
        vm.selectQueue(null)
        val allAfterPause = awaitState(vm) { it.activeCount == 1 && it.pausedCount == 2 }
        assertEquals("file2.mp4", allAfterPause.activeTasks.first().fileName)

        // Mark active task finished and clear finished tasks
        val activeId = allAfterPause.activeTasks.first().id
        vm.markTaskFinished(activeId)
        awaitState(vm) { it.finishedCount == 1 && it.activeCount == 0 }

        vm.clearFinishedTasks()
        awaitState(vm) { it.finishedCount == 0 && it.pausedCount == 2 }

        // Resume all paused tasks
        vm.resumeAllPaused()
        awaitState(vm) { it.activeCount == 2 && it.pausedCount == 0 }

        collectJob.cancel()
    }
}
