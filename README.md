# Memento

**Local AI-powered note memory system for Android.**

Transform scattered notes into a searchable knowledge graph that acts as your external memory.

## Vision

Memento is a "Silent System" that works invisibly in the background:
- Reads all your notes using on-device AI
- Builds a searchable knowledge graph
- Answers contextual queries instantly
- Anticipates when you need information

**Example:** Ask "What did I write about vacation planning?" → System returns relevant notes instantly, even if they're scattered across 10 different files.

## Design Principles

- **Invisible First:** UI only appears when needed
- **Speed as Design:** Every interaction must feel instant
- **Privacy First:** All processing is local, nothing leaves device
- **Silent System:** Works in background, surfaces information proactively

## Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Database:** Room with FTS5 for full-text search
- **Background Processing:** WorkManager + Foreground Service
- **AI/ML:** ONNX Runtime for on-device vector embeddings
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34

## Project Structure

```
studio.modryn.memento
├── data/
│   ├── database/      # Room database, DAOs, entities
│   ├── repository/    # Data access layer
│   ├── parser/        # Markdown/text parsing
│   └── embeddings/    # ONNX vector embedding pipeline
├── service/
│   ├── FileWatcherService.kt   # Foreground service for file monitoring
│   └── NoteProcessingWorker.kt # WorkManager for background processing
├── domain/
│   └── model/         # Domain models
├── ui/
│   ├── search/        # Global search overlay
│   └── theme/         # Material3 theming
└── di/                # Dependency injection (Hilt)
```

## Setup

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

### Building

1. Clone the repository
2. Open in Android Studio
3. Download the ONNX model:
   - Download `all-MiniLM-L6-v2.onnx` from [Hugging Face](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2)
   - Place in `app/src/main/assets/`
   - Also download `vocab.txt` from the same model
4. Sync Gradle
5. Build and run on device

### Required Assets

The following files need to be placed in `app/src/main/assets/`:
- `all-MiniLM-L6-v2.onnx` - Sentence transformer model (~22MB)
- `vocab.txt` - Tokenizer vocabulary

## Phase 1 Features (Weeks 1-2)

- [x] Project setup
- [x] Room database with FTS5
- [x] File watcher service
- [x] ONNX embedding pipeline
- [x] Basic search UI
- [ ] Folder selection UI
- [ ] Permission handling
- [ ] Initial scan workflow

## Phase 2 Features (Weeks 3-4)

- [ ] Knowledge graph layer
- [ ] Entity extraction
- [ ] Temporal awareness
- [ ] Natural language query processing

## Permissions

- `READ_EXTERNAL_STORAGE` / `READ_MEDIA_DOCUMENTS` - Read note files
- `FOREGROUND_SERVICE` - Background file monitoring
- `POST_NOTIFICATIONS` - Service notification
- `SYSTEM_ALERT_WINDOW` - Global search overlay (future)

## License

MIT

---

*"The next 5 years eliminate the need to manage technology at all. Your phone won't feel like a device. It will feel like a silent system working in the background."*
