package studio.modryn.memento.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import studio.modryn.memento.data.database.entity.EmbeddingEntity

/**
 * Data Access Object for vector embeddings.
 * 
 * Handles storage and retrieval of embedding vectors for semantic search.
 * The actual similarity computation happens in Kotlin code since SQLite
 * doesn't natively support vector operations.
 */
@Dao
interface EmbeddingDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbedding(embedding: EmbeddingEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbeddings(embeddings: List<EmbeddingEntity>)
    
    @Query("DELETE FROM embeddings WHERE noteId = :noteId")
    suspend fun deleteEmbeddingsForNote(noteId: String)
    
    @Query("DELETE FROM embeddings")
    suspend fun deleteAllEmbeddings()
    
    @Query("SELECT * FROM embeddings WHERE noteId = :noteId ORDER BY chunkIndex")
    suspend fun getEmbeddingsForNote(noteId: String): List<EmbeddingEntity>
    
    @Query("SELECT * FROM embeddings")
    suspend fun getAllEmbeddings(): List<EmbeddingEntity>
    
    @Query("SELECT COUNT(*) FROM embeddings")
    suspend fun getEmbeddingCount(): Int
    
    /**
     * Get all embeddings with their note info for semantic search.
     * We load all embeddings into memory for vector similarity computation.
     * 
     * For larger datasets, we'd want to implement approximate nearest neighbor
     * search (e.g., HNSW index) but for personal notes (<10k chunks) 
     * brute force is fast enough.
     */
    @Query("""
        SELECT e.*, n.title as noteTitle, n.fileName as noteFileName, n.filePath as noteFilePath
        FROM embeddings e
        JOIN notes n ON e.noteId = n.id
    """)
    suspend fun getAllEmbeddingsWithNoteInfo(): List<EmbeddingWithNote>
}

/**
 * Embedding with parent note metadata for search results.
 */
data class EmbeddingWithNote(
    val id: Long,
    val noteId: String,
    val chunkIndex: Int,
    val chunkText: String,
    val embedding: ByteArray,
    val createdAt: Long,
    val noteTitle: String,
    val noteFileName: String,
    val noteFilePath: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmbeddingWithNote

        if (id != other.id) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
