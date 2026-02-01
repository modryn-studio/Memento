# Memento - Project Instructions

## Project Overview
Memento is a local AI-powered note memory system for Android. It transforms scattered notes into a searchable knowledge graph that acts as external memory.

## Tech Stack
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Database:** Room with FTS5 for full-text search
- **Background Processing:** WorkManager + Foreground Service
- **AI/ML:** ONNX Runtime for on-device vector embeddings (all-MiniLM-L6-v2)
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34

## Package Structure
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

## Design Principles
- **Invisible First:** UI only appears when needed
- **Speed as Design:** Every interaction must feel instant
- **Privacy First:** All processing is local, nothing leaves device
- **Silent System:** Works in background, surfaces information proactively
- **Calm Butler:** Conversational tone, not clinical

## Phase 1 Scope (Weeks 1-2)
- [x] Project setup
- [x] Room database with FTS5
- [x] File watcher service
- [x] Markdown/plain text parsing
- [x] Vector embedding pipeline (ONNX)
- [x] Basic semantic search functionality
- [x] Search overlay UI
- [ ] Folder selection UI
- [ ] Permission handling flow
- [ ] Initial scan workflow

## Key Dependencies
- Room + FTS5 for full-text search
- ONNX Runtime for embeddings
- Jetpack Compose for UI
- Hilt for dependency injection
- WorkManager for background tasks
- DataStore for preferences

## Permissions Required
- READ_EXTERNAL_STORAGE / READ_MEDIA_DOCUMENTS
- FOREGROUND_SERVICE
- POST_NOTIFICATIONS
- SYSTEM_ALERT_WINDOW (Phase 2)

## Development Notes
- Use coroutines for async operations
- Keep UI state in ViewModels
- Use Flow for reactive data
- Test on physical device for file access
- ONNX model files go in app/src/main/assets/

## Voice & Tone
- Use conversational, not clinical messages
- "I couldn't find anything about that" not "0 results found"
- Brief, helpful feedback
