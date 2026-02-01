package studio.modryn.memento.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks scan progress for resumption after interruption.
 * 
 * Only one active scan progress record should exist at a time.
 * This enables:
 * - Resume from checkpoint if app is killed during scan
 * - Show progress UI with files processed/total
 * - Persist progress across app restarts
 */
@Entity(tableName = "scan_progress")
data class ScanProgressEntity(
    @PrimaryKey
    val id: Int = 1, // Only one active scan at a time
    
    val folderPath: String,
    val totalFiles: Int,
    val processedFiles: Int,
    val lastProcessedPath: String?, // For resumption
    
    val startedAt: Long,
    val updatedAt: Long,
    
    val status: String // "IN_PROGRESS", "COMPLETED", "FAILED"
) {
    companion object {
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
    }
    
    val isComplete: Boolean
        get() = status == STATUS_COMPLETED
    
    val progressPercent: Int
        get() = if (totalFiles > 0) (processedFiles * 100) / totalFiles else 0
}
