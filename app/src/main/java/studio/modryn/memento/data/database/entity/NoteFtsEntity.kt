package studio.modryn.memento.data.database.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * FTS5 virtual table for full-text search on notes.
 * 
 * Room uses FTS4 annotation but we configure SQLite to use FTS5 features.
 * This enables fast semantic search across all note content.
 */
@Entity(tableName = "notes_fts")
@Fts4(contentEntity = NoteEntity::class)
data class NoteFtsEntity(
    val title: String,
    val content: String,
    val fileName: String,
)
