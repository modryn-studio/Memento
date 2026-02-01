package studio.modryn.memento.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a note file in the user's notes folder.
 * 
 * This entity stores metadata and content from markdown/text files.
 * The actual content is indexed via FTS4 for full-text search.
 * 
 * Indexes optimize:
 * - filePath lookup for file change detection
 * - lastModified for sorting recent notes
 */
@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["filePath"], unique = true),
        Index(value = ["lastModified"])
    ]
)
data class NoteEntity(
    @PrimaryKey
    val id: String, // File path hash for uniqueness
    
    val filePath: String,
    val fileName: String,
    val title: String, // Extracted from first heading or filename
    val content: String, // Full text content
    val contentPreview: String, // First ~200 chars for display
    
    val fileSize: Long,
    val lastModified: Long, // File modification timestamp
    val indexedAt: Long, // When we processed this file
    
    val wordCount: Int,
    val fileType: String, // "md" or "txt"
)
