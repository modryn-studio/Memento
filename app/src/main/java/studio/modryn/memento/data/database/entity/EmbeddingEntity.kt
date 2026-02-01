package studio.modryn.memento.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores vector embeddings for semantic search.
 * 
 * Each note gets chunked and each chunk gets an embedding vector.
 * This enables finding semantically similar content even when
 * exact keywords don't match.
 */
@Entity(
    tableName = "embeddings",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId")]
)
data class EmbeddingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val noteId: String, // Reference to parent note
    val chunkIndex: Int, // Position of this chunk in the note
    val chunkText: String, // The text that was embedded
    val embedding: ByteArray, // Serialized float array (384 dimensions for MiniLM)
    
    val createdAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmbeddingEntity

        if (id != other.id) return false
        if (noteId != other.noteId) return false
        if (chunkIndex != other.chunkIndex) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + noteId.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
