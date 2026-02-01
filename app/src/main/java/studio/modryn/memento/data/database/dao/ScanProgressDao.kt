package studio.modryn.memento.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import studio.modryn.memento.data.database.entity.ScanProgressEntity

/**
 * Data Access Object for scan progress tracking.
 * 
 * Manages persistent scan progress for:
 * - Resuming interrupted scans
 * - Displaying progress in UI
 * - Tracking scan history
 */
@Dao
interface ScanProgressDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(progress: ScanProgressEntity)
    
    @Update
    suspend fun update(progress: ScanProgressEntity)
    
    @Query("SELECT * FROM scan_progress WHERE id = 1")
    suspend fun getCurrentProgress(): ScanProgressEntity?
    
    @Query("SELECT * FROM scan_progress WHERE id = 1")
    fun observeCurrentProgress(): Flow<ScanProgressEntity?>
    
    @Query("DELETE FROM scan_progress WHERE id = 1")
    suspend fun clearProgress()
    
    @Query("UPDATE scan_progress SET processedFiles = :processedFiles, lastProcessedPath = :lastPath, updatedAt = :updatedAt WHERE id = 1")
    suspend fun updateProgress(processedFiles: Int, lastPath: String?, updatedAt: Long)
    
    @Query("UPDATE scan_progress SET status = :status, updatedAt = :updatedAt WHERE id = 1")
    suspend fun updateStatus(status: String, updatedAt: Long)
}
