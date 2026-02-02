# Memento Design Assets

This directory contains the original design files and high-resolution assets for the Memento brand.

## Brand Identity

**Color Palette:**
- Primary: `#BB86FC` (Soft Purple) - Main accent, calm & trustworthy
- Secondary: `#03DAC6` (Teal) - Highlights and interactive elements
- Background: `#000000` (Pure Black) - OLED-optimized dark theme
- Surface: `#121212` (Dark Gray) - Cards and elevated components
- OnSurface: `#E1E1E1` (Light Gray) - Primary text color

**Typography:**
- Primary: Roboto Medium (weight 500) - Android system default
- Style: Clean, readable, unobtrusive

## Icon Assets

### logomark.svg
Original vector logomark (1024×1024) with glassmorphism design concept:
- Abstract brain/knowledge nodes representing AI-powered memory
- Purple-to-teal gradient for depth and sophistication
- Frosted glass texture with subtle transparency

### ic_launcher_512.png
High-resolution launcher icon (512×512 PNG, 254 KB):
- Required for Google Play Store submission
- Generated from logomark.svg with automatic cropping and padding
- Optimized for app icon display across all platforms

## Implementation

Compiled assets are deployed to:
- `app/src/main/res/mipmap-*dpi/ic_launcher.png` - Launcher icons (5 DPI variants)
- `app/src/main/res/drawable/ic_memento.xml` - Notification icon (24dp vector)

## Regenerating Icons

To regenerate all icon sizes from the logomark:

```powershell
# Requires ImageMagick installed
magick logomark.svg -trim +repage -background none -gravity center -extent 600x600 -resize 512x512 ic_launcher_512.png

# Generate all DPI variants
magick ic_launcher_512.png -resize 48x48 ../app/src/main/res/mipmap-mdpi/ic_launcher.png
magick ic_launcher_512.png -resize 72x72 ../app/src/main/res/mipmap-hdpi/ic_launcher.png
magick ic_launcher_512.png -resize 96x96 ../app/src/main/res/mipmap-xhdpi/ic_launcher.png
magick ic_launcher_512.png -resize 144x144 ../app/src/main/res/mipmap-xxhdpi/ic_launcher.png
magick ic_launcher_512.png -resize 192x192 ../app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
```

## Design Philosophy

**Invisible First:** Design only surfaces when needed  
**Speed as Design:** Every interaction must feel instant  
**Calm Butler:** Conversational tone, not clinical  
**Privacy First:** All branding reinforces local-only processing
