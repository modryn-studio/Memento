# Memento App Module

See project [README.md](../README.md) for full documentation.

## Quick Start

1. Open project in Android Studio
2. Add compressed ONNX model files to `app/src/main/assets/`:
   - `all-MiniLM-L6-v2.onnx.gz` (~8MB compressed)
   - `vocab.txt.gz` (~200KB compressed)
3. Sync Gradle and build
4. Run on physical device (file access requires real device)
5. Model will be decompressed automatically on first launch

## Architecture

- **Pattern:** MVVM with Hilt for dependency injection
- **Database:** Room v3 with FTS4 for search, indexes on filePath/lastModified
- **AI/ML:** ONNX Runtime with multi-threading and graph optimizations
- **UI:** Jetpack Compose with Material3 dark theme
- **Background:** WorkManager for scanning + Foreground Service for file watching
- **Storage:** DataStore for preferences, SAF for folder selection

## Performance Optimizations

- Parallel semantic search with coroutines (4-way split)
- Tokenizer LRU cache (500 entries) + IntArray output
- Loop-unrolled cosine similarity and normalization
- File event debouncing (500ms)
- Database indexes for fast lookups

## Icon Assets

Custom Memento branding with glassmorphism design (purple #BB86FC + teal #03DAC6):

- **Launcher Icons:** `res/mipmap-*dpi/ic_launcher.png` (48px to 192px across 5 DPI buckets)
- **Notification Icon:** `res/drawable/ic_memento.xml` (24dp vector drawable)
- **Play Store:** `../design/ic_launcher_512.png` (512×512 PNG, 254 KB)

Original design files in [`../design/`](../design/). See design README for regeneration instructions.
