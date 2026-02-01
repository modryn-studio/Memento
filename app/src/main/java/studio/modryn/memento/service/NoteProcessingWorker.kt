package studio.modryn.memento.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import studio.modryn.memento.R
import studio.modryn.memento.data.database.dao.ScanProgressDao
import studio.modryn.memento.data.database.entity.ScanProgressEntity
import studio.modryn.memento.data.repository.NoteRepository
import studio.modryn.memento.data.repository.SettingsRepository
import java.io.File

/**
 * Background worker for processing notes with progress tracking.
 * 
 * WorkManager handles:
 * - Initial full scan on first launch (expedited, no constraints)
 * - Periodic re-indexing to catch any missed changes (requires charging)
 * - Processing after permission grants
 * - Resumption of interrupted scans from checkpoint
 * 
 * Progress is emitted every 10 files OR every 2 seconds (whichever comes first)
 * via foreground notification and database checkpoint.
 */
@HiltWorker
class NoteProcessingWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val noteRepository: NoteRepository,
    private val settingsRepository: SettingsRepository,
    private val scanProgressDao: ScanProgressDao
) : CoroutineWorker(context, params) {
    
    companion object {
        const val WORK_NAME = "note_processing"
        const val KEY_OPERATION = "operation"
        const val KEY_FOLDER_PATH = "folder_path"
        
        const val OPERATION_FULL_SCAN = "full_scan"
        const val OPERATION_INCREMENTAL = "incremental"
        const val OPERATION_REINDEX = "reindex"
        const val OPERATION_RESUME = "resume"
        
        // Output keys
        const val KEY_TOTAL_FILES = "total_files"
        const val KEY_PROCESSED_FILES = "processed_files"
        
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "memento_scan_progress"
        
        // Progress throttling: every 10 files OR every 2 seconds
        private const val PROGRESS_FILE_INTERVAL = 10
        private const val PROGRESS_TIME_INTERVAL_MS = 2000L
    }
    
    private var lastProgressUpdateTime = 0L
    private var lastProgressUpdateCount = 0
    
    override suspend fun doWork(): Result {
        val operation = inputData.getString(KEY_OPERATION) ?: OPERATION_INCREMENTAL
        
        val notesFolder = inputData.getString(KEY_FOLDER_PATH) 
            ?: settingsRepository.getNotesFolder()
            ?: return Result.failure() // No folder configured
        
        // Set up as foreground work with notification
        createNotificationChannel()
        setForeground(createForegroundInfo("Preparing to scan notes..."))
        
        return try {
            when (operation) {
                OPERATION_FULL_SCAN -> {
                    performFullScan(notesFolder)
                }
                OPERATION_RESUME -> {
                    resumeScan(notesFolder)
                }
                OPERATION_REINDEX -> {
                    noteRepository.reindexAllEmbeddings()
                    Result.success()
                }
                else -> {
                    performIncrementalScan(notesFolder)
                }
            }
        } catch (e: Exception) {
            // Save checkpoint for potential resumption
            scanProgressDao.updateStatus(
                ScanProgressEntity.STATUS_FAILED,
                System.currentTimeMillis()
            )
            
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
    
    private suspend fun performFullScan(folderPath: String): Result {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            return Result.failure()
        }
        
        // Count total files first
        val allFiles = folder.walkTopDown()
            .filter { it.isFile && (it.extension == "md" || it.extension == "txt") }
            .toList()
        
        val totalFiles = allFiles.size
        
        // Initialize progress tracking
        scanProgressDao.insertOrUpdate(
            ScanProgressEntity(
                folderPath = folderPath,
                totalFiles = totalFiles,
                processedFiles = 0,
                lastProcessedPath = null,
                startedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                status = ScanProgressEntity.STATUS_IN_PROGRESS
            )
        )
        
        // Clear existing data for full rescan
        noteRepository.fullRescan()
        
        // Process each file with progress updates
        allFiles.forEachIndexed { index, file ->
            noteRepository.processFile(file.absolutePath)
            updateProgressIfNeeded(index + 1, totalFiles, file.absolutePath)
        }
        
        // Mark complete
        scanProgressDao.updateStatus(
            ScanProgressEntity.STATUS_COMPLETED,
            System.currentTimeMillis()
        )
        
        return Result.success(
            workDataOf(
                KEY_TOTAL_FILES to totalFiles,
                KEY_PROCESSED_FILES to totalFiles
            )
        )
    }
    
    private suspend fun performIncrementalScan(folderPath: String): Result {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            return Result.failure()
        }
        
        val allFiles = folder.walkTopDown()
            .filter { it.isFile && (it.extension == "md" || it.extension == "txt") }
            .toList()
        
        val totalFiles = allFiles.size
        
        // Initialize progress
        scanProgressDao.insertOrUpdate(
            ScanProgressEntity(
                folderPath = folderPath,
                totalFiles = totalFiles,
                processedFiles = 0,
                lastProcessedPath = null,
                startedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                status = ScanProgressEntity.STATUS_IN_PROGRESS
            )
        )
        
        // Process with progress
        allFiles.forEachIndexed { index, file ->
            noteRepository.processFile(file.absolutePath)
            updateProgressIfNeeded(index + 1, totalFiles, file.absolutePath)
        }
        
        scanProgressDao.updateStatus(
            ScanProgressEntity.STATUS_COMPLETED,
            System.currentTimeMillis()
        )
        
        return Result.success(
            workDataOf(
                KEY_TOTAL_FILES to totalFiles,
                KEY_PROCESSED_FILES to totalFiles
            )
        )
    }
    
    private suspend fun resumeScan(folderPath: String): Result {
        val existingProgress = scanProgressDao.getCurrentProgress()
        
        if (existingProgress == null || existingProgress.isComplete) {
            // No previous scan to resume, do incremental
            return performIncrementalScan(folderPath)
        }
        
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            return Result.failure()
        }
        
        val allFiles = folder.walkTopDown()
            .filter { it.isFile && (it.extension == "md" || it.extension == "txt") }
            .toList()
            .sortedBy { it.absolutePath }
        
        // Find resume point
        val lastProcessedPath = existingProgress.lastProcessedPath
        val startIndex = if (lastProcessedPath != null) {
            allFiles.indexOfFirst { it.absolutePath > lastProcessedPath }.coerceAtLeast(0)
        } else {
            0
        }
        
        val totalFiles = allFiles.size
        var processedFiles = existingProgress.processedFiles
        
        // Resume processing
        for (i in startIndex until allFiles.size) {
            val file = allFiles[i]
            noteRepository.processFile(file.absolutePath)
            processedFiles++
            updateProgressIfNeeded(processedFiles, totalFiles, file.absolutePath)
        }
        
        scanProgressDao.updateStatus(
            ScanProgressEntity.STATUS_COMPLETED,
            System.currentTimeMillis()
        )
        
        return Result.success(
            workDataOf(
                KEY_TOTAL_FILES to totalFiles,
                KEY_PROCESSED_FILES to processedFiles
            )
        )
    }
    
    private suspend fun updateProgressIfNeeded(
        processedFiles: Int,
        totalFiles: Int,
        lastPath: String
    ) {
        val currentTime = System.currentTimeMillis()
        val filesSinceLastUpdate = processedFiles - lastProgressUpdateCount
        val timeSinceLastUpdate = currentTime - lastProgressUpdateTime
        
        // Update every 10 files OR every 2 seconds
        if (filesSinceLastUpdate >= PROGRESS_FILE_INTERVAL || 
            timeSinceLastUpdate >= PROGRESS_TIME_INTERVAL_MS ||
            processedFiles == totalFiles) {
            
            // Update database checkpoint
            scanProgressDao.updateProgress(
                processedFiles = processedFiles,
                lastPath = lastPath,
                updatedAt = currentTime
            )
            
            // Update notification
            val message = "Reading your notes... ($processedFiles of $totalFiles)"
            setForeground(createForegroundInfo(message, processedFiles, totalFiles))
            
            lastProgressUpdateTime = currentTime
            lastProgressUpdateCount = processedFiles
        }
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Scan Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress while reading your notes"
            setShowBadge(false)
        }
        
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createForegroundInfo(
        message: String,
        progress: Int = 0,
        max: Int = 0
    ): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.scan_notification_title))
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .setSilent(true)
            .apply {
                if (max > 0) {
                    setProgress(max, progress, false)
                } else {
                    setProgress(0, 0, true) // Indeterminate
                }
            }
            .build()
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
