package studio.modryn.memento.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import studio.modryn.memento.data.database.dao.EmbeddingDao
import studio.modryn.memento.data.database.dao.NoteDao
import studio.modryn.memento.data.database.dao.ScanProgressDao
import studio.modryn.memento.data.database.entity.EmbeddingEntity
import studio.modryn.memento.data.database.entity.NoteEntity
import studio.modryn.memento.data.database.entity.NoteFtsEntity
import studio.modryn.memento.data.database.entity.ScanProgressEntity

/**
 * Memento Room Database.
 * 
 * Stores all notes and their vector embeddings locally.
 * Uses FTS4 for fast full-text search and custom ByteArray
 * storage for embedding vectors.
 * 
 * Database is the single source of truth for the knowledge graph.
 * 
 * Version history:
 * - v1: Initial schema
 * - v2: Added ScanProgressEntity
 * - v3: Added indexes on NoteEntity (filePath, lastModified)
 */
@Database(
    entities = [
        NoteEntity::class,
        NoteFtsEntity::class,
        EmbeddingEntity::class,
        ScanProgressEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class MementoDatabase : RoomDatabase() {
    
    abstract fun noteDao(): NoteDao
    
    abstract fun embeddingDao(): EmbeddingDao
    
    abstract fun scanProgressDao(): ScanProgressDao
    
    companion object {
        const val DATABASE_NAME = "memento_db"
    }
}
