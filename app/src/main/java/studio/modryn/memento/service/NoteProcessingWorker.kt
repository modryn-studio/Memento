package studio.modryn.memento.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import studio.modryn.memento.data.repository.NoteRepository
import studio.modryn.memento.data.repository.SettingsRepository

/**
 * Background worker for processing notes.
 * 
 * WorkManager handles:
 * - Initial full scan on first launch
 * - Periodic re-indexing to catch any missed changes
 * - Processing after permission grants
 * 
 * This complements the FileWatcherService - the service handles
 * real-time changes, the worker handles batch operations.
 */
@HiltWorker
class NoteProcessingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val noteRepository: NoteRepository,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, params) {
    
    companion object {
        const val WORK_NAME = "note_processing"
        const val KEY_OPERATION = "operation"
        
        const val OPERATION_FULL_SCAN = "full_scan"
        const val OPERATION_INCREMENTAL = "incremental"
        const val OPERATION_REINDEX = "reindex"
    }
    
    override suspend fun doWork(): Result {
        val operation = inputData.getString(KEY_OPERATION) ?: OPERATION_INCREMENTAL
        
        val notesFolder = settingsRepository.getNotesFolder()
            ?: return Result.failure() // No folder configured
        
        return try {
            when (operation) {
                OPERATION_FULL_SCAN -> {
                    noteRepository.fullRescan()
                }
                OPERATION_REINDEX -> {
                    noteRepository.reindexAllEmbeddings()
                }
                else -> {
                    noteRepository.scanFolder(notesFolder)
                }
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
