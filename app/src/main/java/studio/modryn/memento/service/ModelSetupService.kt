package studio.modryn.memento.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import studio.modryn.memento.data.repository.SettingsRepository
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles first-run model decompression from bundled assets.
 * 
 * The ONNX model files are bundled as compressed .gz files to reduce APK size:
 * - all-MiniLM-L6-v2.onnx.gz (~8MB compressed, ~22MB uncompressed)
 * - vocab.txt.gz (~200KB compressed)
 * 
 * On first launch, these are decompressed to internal storage for ONNX Runtime to load.
 */
@Singleton
class ModelSetupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    
    companion object {
        const val MODEL_FILE_COMPRESSED = "all-MiniLM-L6-v2.onnx.gz"
        const val MODEL_FILE = "all-MiniLM-L6-v2.onnx"
        const val VOCAB_FILE_COMPRESSED = "vocab.txt.gz"
        const val VOCAB_FILE = "vocab.txt"
        
        private const val BUFFER_SIZE = 8192
    }
    
    private val _setupProgress = MutableStateFlow<ModelSetupProgress>(ModelSetupProgress.NotStarted)
    val setupProgress: StateFlow<ModelSetupProgress> = _setupProgress.asStateFlow()
    
    /**
     * Directory where decompressed model files are stored.
     */
    val modelDirectory: File
        get() = File(context.filesDir, "models")
    
    /**
     * Path to the decompressed ONNX model file.
     */
    val modelFilePath: String
        get() = File(modelDirectory, MODEL_FILE).absolutePath
    
    /**
     * Path to the decompressed vocabulary file.
     */
    val vocabFilePath: String
        get() = File(modelDirectory, VOCAB_FILE).absolutePath
    
    /**
     * Check if model files are already set up and ready to use.
     */
    suspend fun isModelReady(): Boolean = withContext(Dispatchers.IO) {
        val modelFile = File(modelDirectory, MODEL_FILE)
        val vocabFile = File(modelDirectory, VOCAB_FILE)
        
        // Check both files exist and have reasonable sizes
        modelFile.exists() && modelFile.length() > 1_000_000 && // > 1MB
        vocabFile.exists() && vocabFile.length() > 10_000 // > 10KB
    }
    
    /**
     * Set up model files by decompressing from assets.
     * Reports progress through [setupProgress] StateFlow.
     */
    suspend fun setupModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check if already set up
            if (isModelReady()) {
                _setupProgress.value = ModelSetupProgress.Completed
                settingsRepository.setModelSetupCompleted(true)
                return@withContext true
            }
            
            _setupProgress.value = ModelSetupProgress.InProgress(0, "Preparing...")
            
            // Create model directory
            modelDirectory.mkdirs()
            
            // Check if compressed files exist in assets
            val assetFiles = context.assets.list("") ?: emptyArray()
            val hasCompressedModel = MODEL_FILE_COMPRESSED in assetFiles
            val hasCompressedVocab = VOCAB_FILE_COMPRESSED in assetFiles
            val hasUncompressedModel = MODEL_FILE in assetFiles
            val hasUncompressedVocab = VOCAB_FILE in assetFiles
            
            // Decompress or copy model file
            _setupProgress.value = ModelSetupProgress.InProgress(10, "Setting up AI model...")
            
            when {
                hasCompressedModel -> {
                    decompressAsset(MODEL_FILE_COMPRESSED, File(modelDirectory, MODEL_FILE)) { progress ->
                        _setupProgress.value = ModelSetupProgress.InProgress(
                            10 + (progress * 0.7).toInt(),
                            "Setting up AI model..."
                        )
                    }
                }
                hasUncompressedModel -> {
                    copyAsset(MODEL_FILE, File(modelDirectory, MODEL_FILE)) { progress ->
                        _setupProgress.value = ModelSetupProgress.InProgress(
                            10 + (progress * 0.7).toInt(),
                            "Setting up AI model..."
                        )
                    }
                }
                else -> {
                    _setupProgress.value = ModelSetupProgress.Error("Model file not found in assets")
                    return@withContext false
                }
            }
            
            // Decompress or copy vocab file
            _setupProgress.value = ModelSetupProgress.InProgress(85, "Setting up vocabulary...")
            
            when {
                hasCompressedVocab -> {
                    decompressAsset(VOCAB_FILE_COMPRESSED, File(modelDirectory, VOCAB_FILE)) { progress ->
                        _setupProgress.value = ModelSetupProgress.InProgress(
                            85 + (progress * 0.1).toInt(),
                            "Setting up vocabulary..."
                        )
                    }
                }
                hasUncompressedVocab -> {
                    copyAsset(VOCAB_FILE, File(modelDirectory, VOCAB_FILE)) { progress ->
                        _setupProgress.value = ModelSetupProgress.InProgress(
                            85 + (progress * 0.1).toInt(),
                            "Setting up vocabulary..."
                        )
                    }
                }
                else -> {
                    _setupProgress.value = ModelSetupProgress.Error("Vocabulary file not found in assets")
                    return@withContext false
                }
            }
            
            // Verify setup
            _setupProgress.value = ModelSetupProgress.InProgress(98, "Verifying...")
            
            if (isModelReady()) {
                settingsRepository.setModelSetupCompleted(true)
                _setupProgress.value = ModelSetupProgress.Completed
                true
            } else {
                _setupProgress.value = ModelSetupProgress.Error("Model verification failed")
                false
            }
            
        } catch (e: Exception) {
            _setupProgress.value = ModelSetupProgress.Error(e.message ?: "Setup failed")
            false
        }
    }
    
    /**
     * Decompress a gzipped asset file to the target location.
     */
    private fun decompressAsset(
        assetName: String,
        targetFile: File,
        onProgress: (Int) -> Unit
    ) {
        context.assets.open(assetName).use { assetStream ->
            GZIPInputStream(assetStream).use { gzipStream ->
                FileOutputStream(targetFile).use { outStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    // Estimate uncompressed size (rough: 3x compressed for ONNX)
                    val estimatedSize = assetStream.available() * 3L
                    
                    while (gzipStream.read(buffer).also { bytesRead = it } != -1) {
                        outStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        if (estimatedSize > 0) {
                            val progress = ((totalBytesRead * 100) / estimatedSize).toInt().coerceIn(0, 100)
                            onProgress(progress)
                        }
                    }
                }
            }
        }
        onProgress(100)
    }
    
    /**
     * Copy an uncompressed asset file to the target location.
     */
    private fun copyAsset(
        assetName: String,
        targetFile: File,
        onProgress: (Int) -> Unit
    ) {
        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(targetFile).use { outStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var totalBytesRead = 0L
                val totalSize = inputStream.available().toLong()
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    if (totalSize > 0) {
                        val progress = ((totalBytesRead * 100) / totalSize).toInt().coerceIn(0, 100)
                        onProgress(progress)
                    }
                }
            }
        }
        onProgress(100)
    }
}

/**
 * Progress state for model setup.
 */
sealed class ModelSetupProgress {
    object NotStarted : ModelSetupProgress()
    data class InProgress(val percent: Int, val message: String) : ModelSetupProgress()
    object Completed : ModelSetupProgress()
    data class Error(val message: String) : ModelSetupProgress()
}
