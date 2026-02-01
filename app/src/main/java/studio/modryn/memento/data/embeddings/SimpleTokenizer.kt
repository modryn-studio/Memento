package studio.modryn.memento.data.embeddings

/**
 * Simple WordPiece tokenizer for BERT-based models.
 * 
 * This is a basic implementation that handles:
 * - Word tokenization
 * - WordPiece subword tokenization
 * - Special tokens ([CLS], [SEP], [PAD])
 * - Truncation and padding to max length
 * 
 * For production, consider using a more robust tokenizer library.
 */
class SimpleTokenizer(vocab: List<String>) {
    
    data class TokenizerOutput(
        val inputIds: IntArray,
        val attentionMask: IntArray,
        val tokenTypeIds: IntArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as TokenizerOutput
            return inputIds.contentEquals(other.inputIds)
        }
        override fun hashCode() = inputIds.contentHashCode()
    }
    
    // Use HashMap for O(1) lookup performance
    private val vocabMap: HashMap<String, Int> = HashMap<String, Int>(vocab.size).apply {
        vocab.forEachIndexed { index, token -> put(token, index) }
    }
    private val unkTokenId = vocabMap["[UNK]"] ?: 100
    private val clsTokenId = vocabMap["[CLS]"] ?: 101
    private val sepTokenId = vocabMap["[SEP]"] ?: 102
    private val padTokenId = vocabMap["[PAD]"] ?: 0
    
    // Precompile regex for word splitting
    private val wordSplitRegex = Regex("\\s+")
    
    // LRU cache for frequently tokenized words (common words in notes)
    private val wordCache = object : LinkedHashMap<String, IntArray>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IntArray>?) = size > 500
    }
    
    /**
     * Tokenize text into model inputs.
     * Optimized with preallocated arrays and word caching.
     */
    fun tokenize(text: String, maxLength: Int): TokenizerOutput {
        // Basic cleaning - avoid creating intermediate strings where possible
        val cleanText = text.lowercase().trim()
        
        // Preallocate arrays for better memory performance
        val inputIds = IntArray(maxLength) { padTokenId }
        val attentionMask = IntArray(maxLength) { 0 }
        val tokenTypeIds = IntArray(maxLength) { 0 }
        
        var position = 0
        inputIds[position] = clsTokenId
        attentionMask[position] = 1
        position++
        
        // Word tokenization using precompiled regex
        val words = cleanText.split(wordSplitRegex)
        
        // WordPiece tokenization with caching
        for (word in words) {
            if (position >= maxLength - 1) break
            if (word.isEmpty()) continue
            
            // Check cache first
            val cachedTokens = synchronized(wordCache) { wordCache[word] }
            val wordTokens = cachedTokens ?: tokenizeWord(word).also { tokens ->
                if (word.length <= 20) { // Only cache reasonable-length words
                    synchronized(wordCache) { wordCache[word] = tokens }
                }
            }
            
            for (token in wordTokens) {
                if (position >= maxLength - 1) break
                inputIds[position] = token
                attentionMask[position] = 1
                position++
            }
        }
        
        // Add [SEP] token
        inputIds[position] = sepTokenId
        attentionMask[position] = 1
        
        return TokenizerOutput(inputIds, attentionMask, tokenTypeIds)
    }
    
    private fun tokenizeWord(word: String): IntArray {
        if (word.isEmpty()) return IntArray(0)
        
        // Check if whole word is in vocabulary (fast path)
        vocabMap[word]?.let { return intArrayOf(it) }
        
        // WordPiece tokenization with StringBuilder for prefix
        val tokens = ArrayList<Int>(word.length) // Reasonable initial capacity
        var start = 0
        val prefix = StringBuilder("##")
        
        while (start < word.length) {
            var found = false
            var end = word.length
            
            // Try to find longest matching subword
            while (end > start) {
                val subword = if (tokens.isEmpty()) {
                    word.substring(start, end)
                } else {
                    prefix.setLength(2) // Reset to "##"
                    prefix.append(word, start, end)
                    prefix.toString()
                }
                
                vocabMap[subword]?.let { tokenId ->
                    tokens.add(tokenId)
                    start = end
                    found = true
                    return@let
                }
                end--
            }
            
            if (!found) {
                // Character not in vocab, use [UNK]
                tokens.add(unkTokenId)
                start++
            }
        }
        
        return tokens.toIntArray()
    }
}
