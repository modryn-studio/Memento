package studio.modryn.memento.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import studio.modryn.memento.data.database.entity.NoteEntity

/**
 * Data Access Object for notes.
 * 
 * Provides all database operations for note storage and retrieval.
 * Uses Flow for reactive updates - the UI automatically refreshes
 * when notes change.
 */
@Dao
interface NoteDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)
    
    @Update
    suspend fun updateNote(note: NoteEntity)
    
    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNote(noteId: String)
    
    @Query("DELETE FROM notes WHERE filePath = :filePath")
    suspend fun deleteNoteByPath(filePath: String)
    
    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
    
    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: String): NoteEntity?
    
    @Query("SELECT * FROM notes WHERE filePath = :filePath")
    suspend fun getNoteByPath(filePath: String): NoteEntity?
    
    @Query("SELECT * FROM notes ORDER BY lastModified DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>
    
    @Query("SELECT * FROM notes ORDER BY lastModified DESC LIMIT :limit")
    fun getRecentNotes(limit: Int): Flow<List<NoteEntity>>
    
    @Query("SELECT COUNT(*) FROM notes")
    fun getNoteCount(): Flow<Int>
    
    @Query("SELECT COUNT(*) FROM notes")
    suspend fun getNoteCountSync(): Int
    
    @Query("SELECT filePath FROM notes")
    suspend fun getAllFilePaths(): List<String>
    
    @Query("SELECT id, filePath, lastModified FROM notes")
    suspend fun getNoteMetadata(): List<NoteMetadata>
    
    /**
     * Full-text search using FTS4.
     * Returns notes matching the search query, ranked by relevance.
     */
    @Query("""
        SELECT notes.* FROM notes
        JOIN notes_fts ON notes.id = notes_fts.rowid
        WHERE notes_fts MATCH :query
        ORDER BY bm25(notes_fts) DESC
        LIMIT :limit
    """)
    suspend fun searchNotes(query: String, limit: Int = 20): List<NoteEntity>
    
    /**
     * Search with highlighting - returns matched snippets.
     */
    @Query("""
        SELECT notes.*, snippet(notes_fts, 1, '<b>', '</b>', '...', 32) as matchedSnippet
        FROM notes
        JOIN notes_fts ON notes.id = notes_fts.rowid
        WHERE notes_fts MATCH :query
        ORDER BY bm25(notes_fts) DESC
        LIMIT :limit
    """)
    suspend fun searchNotesWithSnippets(query: String, limit: Int = 20): List<NoteSearchResult>
}

/**
 * Lightweight projection for checking file changes.
 */
data class NoteMetadata(
    val id: String,
    val filePath: String,
    val lastModified: Long
)

/**
 * Search result with highlighted snippet.
 */
data class NoteSearchResult(
    val id: String,
    val filePath: String,
    val fileName: String,
    val title: String,
    val content: String,
    val contentPreview: String,
    val fileSize: Long,
    val lastModified: Long,
    val indexedAt: Long,
    val wordCount: Int,
    val fileType: String,
    val matchedSnippet: String
)
