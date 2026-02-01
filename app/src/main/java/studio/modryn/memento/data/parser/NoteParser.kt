package studio.modryn.memento.data.parser

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parses markdown and plain text files into structured data.
 * 
 * Extracts:
 * - Title (from first H1 heading or filename)
 * - Content (full text without markdown formatting for embedding)
 * - Word count
 * - Metadata (future: tags, links, dates)
 */
@Singleton
class NoteParser @Inject constructor() {
    
    data class ParsedNote(
        val title: String,
        val content: String,
        val rawContent: String,
        val wordCount: Int,
        val links: List<String> = emptyList(),
        val tags: List<String> = emptyList()
    )
    
    /**
     * Parse a file and extract structured content.
     */
    fun parseFile(file: File): ParsedNote? {
        if (!file.exists() || !file.isFile) return null
        
        return try {
            val rawContent = file.readText()
            parse(rawContent, file.nameWithoutExtension)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Parse raw text content.
     */
    fun parse(rawContent: String, fallbackTitle: String): ParsedNote {
        val lines = rawContent.lines()
        
        // Extract title from first H1 heading
        val title = extractTitle(lines, fallbackTitle)
        
        // Clean content for embedding (remove markdown formatting)
        val cleanContent = cleanMarkdown(rawContent)
        
        // Extract internal links [[link]]
        val links = extractLinks(rawContent)
        
        // Extract tags #tag
        val tags = extractTags(rawContent)
        
        // Count words
        val wordCount = cleanContent.split(Regex("\\s+")).count { it.isNotBlank() }
        
        return ParsedNote(
            title = title,
            content = cleanContent,
            rawContent = rawContent,
            wordCount = wordCount,
            links = links,
            tags = tags
        )
    }
    
    private fun extractTitle(lines: List<String>, fallback: String): String {
        // Look for first H1 heading
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("# ")) {
                return trimmed.removePrefix("# ").trim()
            }
        }
        
        // Fallback to first non-empty line
        val firstLine = lines.firstOrNull { it.isNotBlank() }?.trim()
        if (firstLine != null && firstLine.length < 100) {
            return firstLine.removePrefix("#").trim()
        }
        
        return fallback
    }
    
    private fun cleanMarkdown(content: String): String {
        var cleaned = content
        
        // Remove code blocks
        cleaned = cleaned.replace(Regex("```[\\s\\S]*?```"), " ")
        cleaned = cleaned.replace(Regex("`[^`]+`"), " ")
        
        // Remove headings markers but keep text
        cleaned = cleaned.replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
        
        // Remove bold/italic markers
        cleaned = cleaned.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        cleaned = cleaned.replace(Regex("\\*([^*]+)\\*"), "$1")
        cleaned = cleaned.replace(Regex("__([^_]+)__"), "$1")
        cleaned = cleaned.replace(Regex("_([^_]+)_"), "$1")
        
        // Remove links but keep text [text](url) -> text
        cleaned = cleaned.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
        
        // Remove wiki links [[link]] -> link
        cleaned = cleaned.replace(Regex("\\[\\[([^\\]]+)\\]\\]"), "$1")
        
        // Remove images
        cleaned = cleaned.replace(Regex("!\\[[^\\]]*\\]\\([^)]+\\)"), " ")
        
        // Remove horizontal rules
        cleaned = cleaned.replace(Regex("^[-*_]{3,}$", RegexOption.MULTILINE), " ")
        
        // Remove list markers
        cleaned = cleaned.replace(Regex("^[\\s]*[-*+]\\s+", RegexOption.MULTILINE), "")
        cleaned = cleaned.replace(Regex("^[\\s]*\\d+\\.\\s+", RegexOption.MULTILINE), "")
        
        // Remove blockquote markers
        cleaned = cleaned.replace(Regex("^>+\\s*", RegexOption.MULTILINE), "")
        
        // Collapse multiple spaces/newlines
        cleaned = cleaned.replace(Regex("\\s+"), " ")
        
        return cleaned.trim()
    }
    
    private fun extractLinks(content: String): List<String> {
        val linkPattern = Regex("\\[\\[([^\\]]+)\\]\\]")
        return linkPattern.findAll(content).map { it.groupValues[1] }.toList()
    }
    
    private fun extractTags(content: String): List<String> {
        val tagPattern = Regex("#([a-zA-Z][a-zA-Z0-9_-]*)")
        return tagPattern.findAll(content).map { it.groupValues[1] }.toList()
    }
}
