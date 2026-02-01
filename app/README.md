# Memento

See project [README.md](../README.md) for full documentation.

## Quick Start

1. Open project in Android Studio
2. Add ONNX model files to `app/src/main/assets/`
3. Sync Gradle and build
4. Run on physical device (file access requires real device)

## Architecture

- MVVM with Hilt for dependency injection
- Room database with FTS5 for search
- ONNX Runtime for on-device embeddings
- Jetpack Compose for UI
