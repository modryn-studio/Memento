package studio.modryn.memento.ui.theme

import androidx.compose.ui.graphics.Color

// Memento Color Palette
// Dark, calm, invisible - like a butler that appears only when needed

object MementoColors {
    // Background - Pure black for OLED efficiency and "invisible" feel
    val Background = Color(0xFF000000)
    
    // Surfaces - Subtle grays that emerge from the black
    val Surface = Color(0xFF121212)
    val SurfaceVariant = Color(0xFF1E1E1E)
    val SurfaceElevated = Color(0xFF2D2D2D)
    
    // Primary - Soft purple, calm and trustworthy
    val Primary = Color(0xFFBB86FC)
    val PrimaryVariant = Color(0xFF9A67EA)
    
    // Secondary - Teal accent for highlights
    val Secondary = Color(0xFF03DAC6)
    
    // Text colors
    val OnBackground = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFFE1E1E1)
    val OnSurfaceVariant = Color(0xFFA0A0A0)
    val OnSurfaceMuted = Color(0xFF6B6B6B)
    
    // Semantic colors
    val Error = Color(0xFFCF6679)
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFF9800)
    
    // Search overlay specific
    val OverlayBackground = Color(0xE6000000) // 90% opacity black
    val SearchBarBackground = Color(0xFF1A1A1A)
    val ResultCardBackground = Color(0xFF0D0D0D)
}
