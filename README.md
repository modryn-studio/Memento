# Memento

**A magic notebook that remembers everything for you.**

## How It Works

### 1. **Setup (First Time)**
- App asks: "Can I read your notes?" (You say yes)
- App asks: "Where are your notes?" (You point to a folder)
- App downloads a tiny robot brain (22MB) that lives only on your phone
- App reads all your notes and learns what's in them

### 2. **The Invisible Butler**
After setup, the app runs quietly in the background like a helpful robot:
- When you save a new note, it reads it automatically
- When you change a note, it remembers the update
- When you delete a note, it forgets it
- You never have to tell it to do any of this - it just watches and learns

### 3. **Finding Your Memories**
Open the app and type a question like:
- "What did I write about my vacation?"
- "Notes about birthday ideas"
- "That thing mom told me"

The app searches ALL your notes instantly and shows you the right ones - even if you didn't use those exact words.

### 4. **The Smart Part**
The robot brain understands *meaning*, not just matching words:
- You search "trip planning" → finds notes about "vacation" and "travel"
- You search "gift ideas" → finds notes about "presents" and "shopping"

## What Makes It Special

**Privacy:** The robot brain lives on YOUR phone. Your notes never leave. No internet needed.

**Speed:** Answers appear in under 2 seconds.

**Invisible:** You don't organize anything. Just write notes however you want. The system handles everything.

## Example Day

Morning: You write "Buy milk, bread, eggs"
→ App reads it silently

Afternoon: You search "grocery"
→ App shows your morning note instantly

**That's it. You just write. It remembers. You ask. It finds.**

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
- **Database:** Room with FTS4 for full-text search
- **Background Processing:** WorkManager + Foreground Service
- **AI/ML:** ONNX Runtime for on-device vector embeddings (all-MiniLM-L6-v2, 384-dim)
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34

## Performance Features

- **Parallel Semantic Search:** Multi-threaded vector similarity (4-way parallelism)
- **Smart Tokenization:** LRU cache for frequent words, optimized IntArray output
- **ONNX Optimization:** Multi-core inference, graph optimizations, memory pattern optimization
- **Event Debouncing:** 500ms coalescing for file changes (handles rapid editor saves)
- **Database Indexes:** Optimized queries on filePath and lastModified

## Phase 1 Features (Complete ✅)

- [x] Project setup with Hilt DI
- [x] Room database with FTS4 and indexes
- [x] Hybrid FileObserver + SAF polling
- [x] ONNX embedding pipeline with caching
- [x] Onboarding flow (model setup, permissions, folder selection)
- [x] Search UI with "calm butler" voice/tone
- [x] Permission handling with progressive escalation
- [x] Initial scan workflow with progress tracking
- [x] Performance optimizations (parallel search, loop unrolling)
- [x] Custom brand identity with glassmorphism icon design

## Project Structure

```
Memento/
├── app/                    # Android application module
│   ├── src/main/
│   │   ├── assets/         # ONNX model files (compressed)
│   │   ├── java/           # Kotlin source code
│   │   │   └── studio/modryn/memento/
│   │   │       ├── data/   # Database, repositories, embeddings
│   │   │       ├── di/     # Hilt dependency injection
│   │   │       ├── domain/ # Domain models
│   │   │       ├── service/# Background services & workers
│   │   │       └── ui/     # Compose UI & ViewModels
│   │   └── res/            # Android resources
│   │       ├── drawable/   # Vector drawables (notification icons)
│   │       ├── mipmap-*/   # Launcher icons (5 DPI variants)
│   │       └── values/     # Strings, colors, themes
│   └── build.gradle.kts    # App module build config
├── design/                 # Brand assets & design files
│   ├── logomark.svg        # Original vector logomark (1024×1024)
│   ├── ic_launcher_512.png # Play Store icon (512×512)
│   └── README.md           # Brand guidelines & regeneration instructions
├── gradle/                 # Gradle wrapper files
├── .github/                # GitHub configuration
│   └── copilot-instructions.md
├── build.gradle.kts        # Project-level build config
└── README.md              # This file
```

See [`app/README.md`](app/README.md) for module documentation and [`design/README.md`](design/README.md) for brand guidelines.

## Phase 2 Features (Weeks 3-4)

- [ ] Knowledge graph layer
- [ ] Entity extraction
- [ ] Temporal awareness
- [ ] Natural language query processing

## Permissions

- `READ_EXTERNAL_STORAGE` (SDK ≤32) / `READ_MEDIA_DOCUMENTS` (SDK 33+) - Read note files
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` - Background file monitoring
- `POST_NOTIFICATIONS` (SDK 33+) - Scan progress notifications
- `RECEIVE_BOOT_COMPLETED` - Restart service on reboot
- `WAKE_LOCK` - Keep service running
- Storage Access Framework (SAF) - Folder selection on Android 11+
- `SYSTEM_ALERT_WINDOW` - Global search overlay (Phase 2)

## License

MIT

---

*"The next 5 years eliminate the need to manage technology at all. Your phone won't feel like a device. It will feel like a silent system working in the background."*
