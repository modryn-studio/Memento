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
        val inputIds: List<Int>,
        val attentionMask: List<Int>,
        val tokenTypeIds: List<Int>
    )
    
    private val vocabMap: Map<String, Int> = vocab.mapIndexed { index, token -> token to index }.toMap()
    private val unkTokenId = vocabMap["[UNK]"] ?: 100
    private val clsTokenId = vocabMap["[CLS]"] ?: 101
    private val sepTokenId = vocabMap["[SEP]"] ?: 102
    private val padTokenId = vocabMap["[PAD]"] ?: 0
    
    /**
     * Tokenize text into model inputs.
     */
    fun tokenize(text: String, maxLength: Int): TokenizerOutput {
        // Basic cleaning
        val cleanText = text.lowercase().trim()
        
        // Word tokenization
        val words = cleanText.split(Regex("\\s+"))
        
        // WordPiece tokenization
        val tokens = mutableListOf<Int>()
        tokens.add(clsTokenId) // [CLS] token
        
        for (word in words) {
            if (tokens.size >= maxLength - 1) break
            
            val wordTokens = tokenizeWord(word)
            for (token in wordTokens) {
                if (tokens.size >= maxLength - 1) break
                tokens.add(token)
            }
        }
        
        tokens.add(sepTokenId) // [SEP] token
        
        // Pad to max length
        val inputIds = tokens.toMutableList()
        while (inputIds.size < maxLength) {
            inputIds.add(padTokenId)
        }
        
        // Create attention mask (1 for real tokens, 0 for padding)
        val attentionMask = inputIds.map { if (it != padTokenId) 1 else 0 }
        
        // Token type IDs (all 0 for single sentence)
        val tokenTypeIds = List(maxLength) { 0 }
        
        return TokenizerOutput(
            inputIds = inputIds.take(maxLength),
            attentionMask = attentionMask.take(maxLength),
            tokenTypeIds = tokenTypeIds
        )
    }
    
    private fun tokenizeWord(word: String): List<Int> {
        if (word.isEmpty()) return emptyList()
        
        // Check if whole word is in vocabulary
        if (vocabMap.containsKey(word)) {
            return listOf(vocabMap[word]!!)
        }
        
        // WordPiece tokenization
        val tokens = mutableListOf<Int>()
        var remaining = word
        
        while (remaining.isNotEmpty()) {
            var found = false
            
            // Try to find longest matching subword
            for (end in remaining.length downTo 1) {
                val subword = if (tokens.isEmpty()) {
                    remaining.substring(0, end)
                } else {
                    "##" + remaining.substring(0, end)
                }
                
                if (vocabMap.containsKey(subword)) {
                    tokens.add(vocabMap[subword]!!)
                    remaining = remaining.substring(end)
                    found = true
                    break
                }
            }
            
            if (!found) {
                // Character not in vocab, use [UNK]
                tokens.add(unkTokenId)
                remaining = remaining.drop(1)
            }
        }
        
        return tokens
    }
}
