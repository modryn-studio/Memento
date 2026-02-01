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
import java.io.File
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
 * 
 * Model files are decompressed on first run by ModelSetupService
 * and stored in internal storage for faster loading.
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
     * Directory where model files are stored (set up by ModelSetupService).
     */
    private val modelDirectory: File
        get() = File(context.filesDir, "models")
    
    /**
     * Check if model files exist and are ready for use.
     */
    fun isModelAvailable(): Boolean {
        val modelFile = File(modelDirectory, MODEL_FILE)
        val vocabFile = File(modelDirectory, TOKENIZER_VOCAB)
        return modelFile.exists() && modelFile.length() > 1_000_000 &&
               vocabFile.exists() && vocabFile.length() > 10_000
    }
    
    /**
     * Initialize the ONNX runtime and load the model.
     * Model files must be set up by ModelSetupService first.
     * Optimized with session options for mobile inference.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isInitialized) return@withContext true
            
            try {
                // Check if model files are available
                if (!isModelAvailable()) {
                    return@withContext false
                }
                
                // Initialize ONNX Runtime
                ortEnvironment = OrtEnvironment.getEnvironment()
                
                // Load model from internal storage (set up by ModelSetupService)
                val modelFile = File(modelDirectory, MODEL_FILE)
                val modelBytes = modelFile.readBytes()
                
                // Configure session options for optimal mobile performance
                val sessionOptions = OrtSession.SessionOptions().apply {
                    // Use all available CPU cores
                    setIntraOpNumThreads(Runtime.getRuntime().availableProcessors())
                    // Enable graph optimizations
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    // Reduce memory usage
                    setMemoryPatternOptimization(true)
                }
                
                ortSession = ortEnvironment?.createSession(modelBytes, sessionOptions)
                
                // Load tokenizer vocabulary from internal storage
                val vocabFile = File(modelDirectory, TOKENIZER_VOCAB)
                val vocab = vocabFile.bufferedReader().readLines()
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
     * Optimized with reusable buffers and reduced allocations.
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
                
                // Tokenize input (now returns IntArray directly)
                val tokens = tok.tokenize(text, MAX_SEQUENCE_LENGTH)
                
                // Create input tensors - use LongArray directly without intermediate conversion
                val inputIdsLong = LongArray(tokens.inputIds.size) { tokens.inputIds[it].toLong() }
                val attentionMaskLong = LongArray(tokens.attentionMask.size) { tokens.attentionMask[it].toLong() }
                val tokenTypeIdsLong = LongArray(tokens.tokenTypeIds.size) { tokens.tokenTypeIds[it].toLong() }
                
                val inputIds = OnnxTensor.createTensor(env, arrayOf(inputIdsLong))
                val attentionMask = OnnxTensor.createTensor(env, arrayOf(attentionMaskLong))
                val tokenTypeIds = OnnxTensor.createTensor(env, arrayOf(tokenTypeIdsLong))
                
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
    
    /**
     * Mean pooling with attention mask (optimized for IntArray).
     */
    private fun meanPooling(tokenEmbeddings: Array<FloatArray>, attentionMask: IntArray): FloatArray {
        val embedding = FloatArray(EMBEDDING_DIMENSION)
        var count = 0f
        
        for (i in tokenEmbeddings.indices) {
            if (attentionMask[i] == 1) {
                val tokenEmb = tokenEmbeddings[i]
                for (j in embedding.indices) {
                    embedding[j] += tokenEmb[j]
                }
                count++
            }
        }
        
        if (count > 0) {
            val invCount = 1f / count
            for (j in embedding.indices) {
                embedding[j] *= invCount
            }
        }
        
        return embedding
    }
    
    /**
     * Normalize embedding vector in-place (optimized with loop unrolling).
     */
    private fun normalize(embedding: FloatArray) {
        var norm = 0f
        val len = embedding.size
        val unrolledLen = len - (len % 4)
        var i = 0
        
        // Calculate norm with loop unrolling
        while (i < unrolledLen) {
            norm += embedding[i] * embedding[i] +
                    embedding[i + 1] * embedding[i + 1] +
                    embedding[i + 2] * embedding[i + 2] +
                    embedding[i + 3] * embedding[i + 3]
            i += 4
        }
        while (i < len) {
            norm += embedding[i] * embedding[i]
            i++
        }
        
        norm = kotlin.math.sqrt(norm)
        
        if (norm > 0) {
            val invNorm = 1f / norm
            for (j in embedding.indices) {
                embedding[j] *= invNorm
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
