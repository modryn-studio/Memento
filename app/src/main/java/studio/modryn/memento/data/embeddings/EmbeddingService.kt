package studio.modryn.memento.data.embeddings

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ONNX-based embedding service for semantic search.
 * 
 * Uses a sentence-transformers model (MiniLM) converted to ONNX format
 * to generate 384-dimensional embeddings for text chunks.
 * 
 * The model runs entirely on-device for privacy.
 * 
 * Model: all-MiniLM-L6-v2 (22MB, 384 dimensions)
 * - Fast inference on mobile
 * - Good quality for English text
 * - Can be upgraded to larger models later
 */
@Singleton
class EmbeddingService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        const val EMBEDDING_DIMENSION = 384
        const val MODEL_FILE = "all-MiniLM-L6-v2.onnx"
        const val TOKENIZER_VOCAB = "vocab.txt"
        const val MAX_SEQUENCE_LENGTH = 256
    }
    
    private var ortEnvironment: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: SimpleTokenizer? = null
    private val mutex = Mutex()
    
    private var isInitialized = false
    
    /**
     * Initialize the ONNX runtime and load the model.
     * Call this once at app startup.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isInitialized) return@withContext true
            
            try {
                // Initialize ONNX Runtime
                ortEnvironment = OrtEnvironment.getEnvironment()
                
                // Load model from assets
                val modelBytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
                
                ortSession = ortEnvironment?.createSession(
                    modelBytes,
                    OrtSession.SessionOptions()
                )
                
                // Load tokenizer vocabulary
                val vocab = context.assets.open(TOKENIZER_VOCAB).bufferedReader().readLines()
                tokenizer = SimpleTokenizer(vocab)
                
                isInitialized = true
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Generate embedding vector for text.
     * Returns null if model not initialized or inference fails.
     */
    suspend fun generateEmbedding(text: String): FloatArray? = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            if (!initialize()) return@withContext null
        }
        
        mutex.withLock {
            try {
                val session = ortSession ?: return@withContext null
                val env = ortEnvironment ?: return@withContext null
                val tok = tokenizer ?: return@withContext null
                
                // Tokenize input
                val tokens = tok.tokenize(text, MAX_SEQUENCE_LENGTH)
                
                // Create input tensors
                val inputIds = OnnxTensor.createTensor(
                    env,
                    arrayOf(tokens.inputIds.map { it.toLong() }.toLongArray())
                )
                val attentionMask = OnnxTensor.createTensor(
                    env,
                    arrayOf(tokens.attentionMask.map { it.toLong() }.toLongArray())
                )
                val tokenTypeIds = OnnxTensor.createTensor(
                    env,
                    arrayOf(tokens.tokenTypeIds.map { it.toLong() }.toLongArray())
                )
                
                // Run inference
                val inputs = mapOf(
                    "input_ids" to inputIds,
                    "attention_mask" to attentionMask,
                    "token_type_ids" to tokenTypeIds
                )
                
                val results = session.run(inputs)
                
                // Extract embeddings (mean pooling over token embeddings)
                val outputTensor = results[0].value as Array<Array<FloatArray>>
                val tokenEmbeddings = outputTensor[0]
                
                // Mean pooling with attention mask
                val embedding = meanPooling(tokenEmbeddings, tokens.attentionMask)
                
                // Normalize
                normalize(embedding)
                
                // Clean up
                inputIds.close()
                attentionMask.close()
                tokenTypeIds.close()
                results.close()
                
                embedding
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Serialize embedding to ByteArray for database storage.
     */
    fun serializeEmbedding(embedding: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(embedding.size * 4)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        embedding.forEach { buffer.putFloat(it) }
        return buffer.array()
    }
    
    /**
     * Deserialize ByteArray back to FloatArray.
     */
    fun deserializeEmbedding(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val floatBuffer = buffer.asFloatBuffer()
        val embedding = FloatArray(floatBuffer.remaining())
        floatBuffer.get(embedding)
        return embedding
    }
    
    private fun meanPooling(tokenEmbeddings: Array<FloatArray>, attentionMask: List<Int>): FloatArray {
        val embedding = FloatArray(EMBEDDING_DIMENSION)
        var count = 0f
        
        for (i in tokenEmbeddings.indices) {
            if (attentionMask[i] == 1) {
                for (j in embedding.indices) {
                    embedding[j] += tokenEmbeddings[i][j]
                }
                count++
            }
        }
        
        if (count > 0) {
            for (j in embedding.indices) {
                embedding[j] /= count
            }
        }
        
        return embedding
    }
    
    private fun normalize(embedding: FloatArray) {
        var norm = 0f
        for (value in embedding) {
            norm += value * value
        }
        norm = kotlin.math.sqrt(norm)
        
        if (norm > 0) {
            for (i in embedding.indices) {
                embedding[i] /= norm
            }
        }
    }
    
    /**
     * Clean up resources.
     */
    fun close() {
        ortSession?.close()
        ortEnvironment?.close()
        ortSession = null
        ortEnvironment = null
        isInitialized = false
    }
}
