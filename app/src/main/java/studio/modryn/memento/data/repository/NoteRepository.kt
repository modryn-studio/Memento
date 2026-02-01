package studio.modryn.memento.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import studio.modryn.memento.data.database.dao.EmbeddingDao
import studio.modryn.memento.data.database.dao.NoteDao
import studio.modryn.memento.data.database.dao.NoteSearchResult
import studio.modryn.memento.data.database.entity.EmbeddingEntity
import studio.modryn.memento.data.database.entity.NoteEntity
import studio.modryn.memento.data.embeddings.EmbeddingService
import studio.modryn.memento.data.parser.NoteParser
import studio.modryn.memento.domain.model.SearchResult
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for note operations.
 * 
 * This is the core data layer that handles:
 * - File scanning and parsing
 * - Database operations
 * - Embedding generation
 * - Search (both FTS and semantic)
 * 
 * The repository coordinates between the file system, database,
 * and embedding service to maintain the knowledge graph.
 */
@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val embeddingDao: EmbeddingDao,
    private val noteParser: NoteParser,
    private val embeddingService: EmbeddingService,
    private val settingsRepository: SettingsRepository
) {
    
    /**
     * Get all notes as a Flow for reactive UI updates.
     */
    fun getAllNotes(): Flow<List<NoteEntity>> = noteDao.getAllNotes()
    
    /**
     * Get recent notes for display.
     */
    fun getRecentNotes(limit: Int = 10): Flow<List<NoteEntity>> = noteDao.getRecentNotes(limit)
    
    /**
     * Get total note count as a Flow.
     */
    fun getNoteCount(): Flow<Int> = noteDao.getNoteCount()
    
    /**
     * Scan a folder and process all markdown/text files.
     */
    suspend fun scanFolder(folderPath: String) = withContext(Dispatchers.IO) {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) return@withContext
        
        val existingPaths = noteDao.getAllFilePaths().toSet()
        val currentPaths = mutableSetOf<String>()
        
        // Find all note files
        folder.walkTopDown()
            .filter { it.isFile && (it.extension == "md" || it.extension == "txt") }
            .forEach { file ->
                currentPaths.add(file.absolutePath)
                processFile(file.absolutePath)
            }
        
        // Remove notes for deleted files
        val deletedPaths = existingPaths - currentPaths
        deletedPaths.forEach { path ->
            noteDao.deleteNoteByPath(path)
        }
    }
    
    /**
     * Process a single file - parse, store, and generate embeddings.
     */
    suspend fun processFile(filePath: String) = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext
        
        val noteId = generateNoteId(filePath)
        
        // Check if file has changed
        val existingNote = noteDao.getNoteById(noteId)
        if (existingNote != null && existingNote.lastModified >= file.lastModified()) {
            return@withContext // File unchanged, skip processing
        }
        
        // Parse the file
        val parsedNote = noteParser.parseFile(file) ?: return@withContext
        
        // Create note entity
        val noteEntity = NoteEntity(
            id = noteId,
            filePath = filePath,
            fileName = file.name,
            title = parsedNote.title,
            content = parsedNote.content,
            contentPreview = parsedNote.content.take(200),
            fileSize = file.length(),
            lastModified = file.lastModified(),
            indexedAt = System.currentTimeMillis(),
            wordCount = parsedNote.wordCount,
            fileType = file.extension
        )
        
        // Save to database
        noteDao.insertNote(noteEntity)
        
        // Generate and store embeddings
        generateEmbeddings(noteId, parsedNote.content)
    }
    
    /**
     * Remove a file from the index.
     */
    suspend fun removeFile(filePath: String) = withContext(Dispatchers.IO) {
        noteDao.deleteNoteByPath(filePath)
        // Embeddings are deleted via CASCADE
    }
    
    /**
     * Full rescan - clear everything and reindex.
     */
    suspend fun fullRescan() = withContext(Dispatchers.IO) {
        val folderPath = settingsRepository.getNotesFolder() ?: return@withContext
        
        noteDao.deleteAllNotes()
        embeddingDao.deleteAllEmbeddings()
        
        scanFolder(folderPath)
    }
    
    /**
     * Regenerate all embeddings (useful after model update).
     */
    suspend fun reindexAllEmbeddings() = withContext(Dispatchers.IO) {
        embeddingDao.deleteAllEmbeddings()
        
        val notes = noteDao.getAllFilePaths()
        notes.forEach { path ->
            val note = noteDao.getNoteByPath(path)
            if (note != null) {
                generateEmbeddings(note.id, note.content)
            }
        }
    }
    
    /**
     * Full-text search using FTS4.
     */
    suspend fun searchFts(query: String, limit: Int = 20): List<NoteSearchResult> {
        return withContext(Dispatchers.IO) {
            // Convert natural language to FTS query
            val ftsQuery = query
                .split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { "$it*" } // Prefix matching
            
            noteDao.searchNotesWithSnippets(ftsQuery, limit)
        }
    }
    
    /**
     * Semantic search using vector embeddings.
     */
    suspend fun searchSemantic(query: String, limit: Int = 10): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            // Generate embedding for query
            val queryEmbedding = embeddingService.generateEmbedding(query)
                ?: return@withContext emptyList()
            
            // Get all embeddings
            val allEmbeddings = embeddingDao.getAllEmbeddingsWithNoteInfo()
            
            // Calculate similarity and rank
            allEmbeddings
                .map { embeddingWithNote ->
                    val embedding = embeddingService.deserializeEmbedding(embeddingWithNote.embedding)
                    val similarity = cosineSimilarity(queryEmbedding, embedding)
                    
                    SearchResult(
                        noteId = embeddingWithNote.noteId,
                        noteTitle = embeddingWithNote.noteTitle,
                        noteFileName = embeddingWithNote.noteFileName,
                        filePath = embeddingWithNote.noteFilePath,
                        matchedText = embeddingWithNote.chunkText,
                        score = similarity,
                        matchType = SearchResult.MatchType.SEMANTIC
                    )
                }
                .filter { it.score > 0.3f } // Minimum similarity threshold
                .sortedByDescending { it.score }
                .distinctBy { it.noteId } // One result per note
                .take(limit)
        }
    }
    
    /**
     * Combined search - semantic first, then FTS for breadth.
     */
    suspend fun search(query: String, limit: Int = 10): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            val semanticResults = searchSemantic(query, limit)
            val ftsResults = searchFts(query, limit)
            
            // Combine results, preferring semantic matches
            val combined = mutableListOf<SearchResult>()
            val seenNoteIds = mutableSetOf<String>()
            
            // Add semantic results first
            semanticResults.forEach { result ->
                combined.add(result)
                seenNoteIds.add(result.noteId)
            }
            
            // Add FTS results that weren't in semantic
            ftsResults.forEach { ftsResult ->
                if (ftsResult.id !in seenNoteIds) {
                    combined.add(
                        SearchResult(
                            noteId = ftsResult.id,
                            noteTitle = ftsResult.title,
                            noteFileName = ftsResult.fileName,
                            filePath = ftsResult.filePath,
                            matchedText = ftsResult.matchedSnippet,
                            score = 0.5f, // Default score for FTS
                            matchType = SearchResult.MatchType.KEYWORD
                        )
                    )
                    seenNoteIds.add(ftsResult.id)
                }
            }
            
            combined.take(limit)
        }
    }
    
    private suspend fun generateEmbeddings(noteId: String, content: String) {
        // Split content into chunks
        val chunks = chunkText(content)
        
        // Generate embeddings for each chunk
        val embeddings = chunks.mapIndexedNotNull { index, chunk ->
            val embedding = embeddingService.generateEmbedding(chunk)
            if (embedding != null) {
                EmbeddingEntity(
                    noteId = noteId,
                    chunkIndex = index,
                    chunkText = chunk,
                    embedding = embeddingService.serializeEmbedding(embedding),
                    createdAt = System.currentTimeMillis()
                )
            } else null
        }
        
        // Delete old embeddings and insert new ones
        embeddingDao.deleteEmbeddingsForNote(noteId)
        embeddingDao.insertEmbeddings(embeddings)
    }
    
    private fun chunkText(text: String, maxChunkSize: Int = 500, overlap: Int = 50): List<String> {
        if (text.length <= maxChunkSize) return listOf(text)
        
        val chunks = mutableListOf<String>()
        var start = 0
        
        while (start < text.length) {
            val end = minOf(start + maxChunkSize, text.length)
            
            // Try to break at sentence boundary
            val chunk = text.substring(start, end)
            val lastPeriod = chunk.lastIndexOf('.')
            val actualEnd = if (lastPeriod > maxChunkSize / 2) {
                start + lastPeriod + 1
            } else {
                end
            }
            
            chunks.add(text.substring(start, actualEnd).trim())
            start = actualEnd - overlap
        }
        
        return chunks.filter { it.isNotBlank() }
    }
    
    private fun generateNoteId(filePath: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(filePath.toByteArray())
        return hash.take(16).joinToString("") { "%02x".format(it) }
    }
    
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        
        val denominator = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denominator > 0) dotProduct / denominator else 0f
    }
}
