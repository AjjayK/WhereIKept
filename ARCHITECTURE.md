# WhereIKept - Architecture & Technical Guide

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer (Compose)                    │
│  CaptureScreen (9-state workflow)                       │
│  SearchScreen, ItemsListScreen, SettingsScreen          │
│  MainScreen (tab navigation)                            │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                  ViewModel Layer (MVVM)                  │
│  CaptureViewModel (state machine)                       │
│  MainViewModel (search & items)                         │
│  GemmaDownloadViewModel (model download)                │
│  AnalyticsViewModel (metrics & consent)                 │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                   Service/Util Layer                     │
│  SpeechRecognitionHelper (Whisper JNI)                  │
│  LlmService (Gemma 3N via LiteRT-LM)                   │
│  HuggingFaceAuthHelper (OAuth)                          │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                  Repository Layer                        │
│  WhereIKeptRepository (data operations)                 │
│  GemmaDownloadRepository (download management)          │
│  AnalyticsRepository (metrics storage & sync)           │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                   Data Layer (Room)                      │
│  ItemEntity, RecordingEntity, ImageEntity               │
│  ItemFts (full-text search)                             │
│  Analytics: InferenceMetricEntity, DeviceMetricEntity   │
│  DAOs: ItemDao, RecordingDao, ImageDao, MetricsDao      │
└─────────────────────────────────────────────────────────┘
```

## Core Components

### 1. Speech Recognition (Whisper)

**Files:** `SpeechRecognitionHelper.kt`, `LibWhisper.kt`, `whisper_jni.cpp`

- Whisper.cpp integration via JNI (C++ native library)
- Model: `ggml-tiny.en.bin` (74 MB), 16kHz mono PCM audio
- Auto-copies model from assets on first launch
- Records audio → saves as WAV → transcribes via native `whisper_full()`
- 4 CPU threads, greedy sampling strategy, English-only
- Model released from memory after transcription, re-initialized on next recording

### 2. LLM Service (Gemma 3N)

**File:** `LlmService.kt`

- Google Gemma 3N E2B via LiteRT-LM SDK
- Multimodal: text + image analysis
- Backend auto-selection: GPU (OpenCL) → CPU (XNNPACK) → NPU (QNN)
- Streaming inference via Kotlin Flow for TTFT measurement
- Key methods:
  - `extractItemsFromTranscription()` — text-only extraction
  - `extractItemsWithImage()` — multimodal extraction (transcript + image)
  - `generateTagsForDatabase()` — convert edited tags to DB format

### 3. Model Download System

**Files:** `GemmaDownloadWorker.kt`, `GemmaDownloadRepository.kt`, `HuggingFaceAuthHelper.kt`

- Background download with WorkManager and foreground service
- HuggingFace OAuth (AppAuth) for gated model access
- Resume capability for interrupted downloads
- Progress tracking with speed, percentage, ETA
- Model stored at: `Android/data/com.whereikept.app/files/models/`

### 4. Analytics System

**Files:** `AnalyticsRepository.kt`, `InferenceMetricsCollector.kt`, `ResourceMonitor.kt`, `DeviceInfoCollector.kt`

Three-tier metrics collection:
1. **Version metrics** (one-time per version) — LLM config, prompt version, model variant
2. **Device metrics** (one-time per device) — hardware specs, AI acceleration capabilities
3. **Inference metrics** (per LLM call) — timing, tokens, resources, reliability

Privacy-first: opt-in consent, local Room storage always on, Firebase only with consent.

## Capture Workflow (9-State Machine)

```
IDLE → RECORDING → IMAGE_CAPTURE → TRANSCRIBING → ANALYZING
  → REVIEW_EDITING → SUBMITTING → SUCCESS → [back to IDLE]
                                      ↓
                                   ERROR (with retry/skip)
```

| State | Description |
|-------|-------------|
| IDLE | Start recording button |
| RECORDING | Audio capture with visualization (Whisper runs on stop) |
| IMAGE_CAPTURE | Take photo or skip (transcription runs in parallel) |
| TRANSCRIBING | Whisper speech-to-text (if not complete) |
| ANALYZING | Gemma 3N multimodal analysis (text + image) |
| REVIEW_EDITING | Draggable tags on image, manual editing |
| SUBMITTING | Save to database |
| SUCCESS | Confirmation with 3s auto-reset |
| ERROR | Error handling with retry/skip options |

### Error Recovery

| Error Type | Retryable | Skippable | Action |
|------------|-----------|-----------|--------|
| PERMISSION_DENIED | No | No | Show settings prompt |
| WHISPER_FAILED | Yes | No | Retry transcription |
| GEMMA_FAILED | Yes | Yes | Retry or skip to manual entry |
| CAMERA_FAILED | Yes | Yes | Retry or skip image |
| DB_ERROR | Yes | No | Retry save |

## Data Model

### ItemEntity (Database)
```kotlin
{
  id: Long,
  objectName: String,           // "keys"
  location: String,             // "kitchen drawer"
  description: String,          // Additional context
  objectAttribute: String?,     // "red", "metal"
  locationParent: String?,      // "home", "office"
  imagePath: String?,           // Original image URI
  taggedImagePath: String?,     // Image with tag overlays
  timestamp: Long,
  sourceType: String            // "voice", "image", "manual"
}
```

### Data Flow
```
User Input → ViewModel (State Machine) → Services (Whisper, Gemma)
  → Repository (Room DB) → UI State Updates (StateFlow) → Composables
```

## AI/ML Stack

### Whisper (Speech-to-Text)
- Model: `ggml-tiny.en.bin` (74 MB)
- Sample rate: 16kHz mono PCM
- Native C++ via JNI, 4 threads
- Speed: 2-3x real-time (3s audio → 6-9s processing)

### Gemma 3N (LLM Inference)
- Model: `gemma-3n-e2b-it-int4.litertlm` (~3 GB)
- Format: `.litertlm` (recommended), also supports `.task`, `.bin`, `.tflite`
- Performance benchmarks (Pixel 6):
  - GPU: 23.3 tokens/sec (OpenCL)
  - CPU: 17.6 tokens/sec (XNNPACK)
  - NPU: 50-80+ tokens/sec (Qualcomm QNN, if available)

## Project Structure

```
WhereIKept/
├── app/src/main/
│   ├── java/com/whereikept/app/
│   │   ├── auth/
│   │   │   └── HuggingFaceAuthHelper.kt       # OAuth for gated models
│   │   ├── data/
│   │   │   ├── Database.kt                     # Room database
│   │   │   ├── Entities.kt                     # Data models
│   │   │   ├── Daos.kt                         # Data access
│   │   │   ├── Repository.kt                   # Data repository
│   │   │   ├── AnalyticsEntities.kt            # Metrics entities
│   │   │   ├── AnalyticsRepository.kt          # Metrics repository
│   │   │   └── GemmaModel.kt                   # Model config
│   │   ├── repository/
│   │   │   └── GemmaDownloadRepository.kt      # Download management
│   │   ├── worker/
│   │   │   └── GemmaDownloadWorker.kt          # Background downloads
│   │   ├── viewmodel/
│   │   │   ├── CaptureViewModel.kt             # Capture state machine
│   │   │   ├── CaptureModels.kt                # Capture data models
│   │   │   ├── MainViewModel.kt                # Search & items
│   │   │   ├── GemmaDownloadViewModel.kt       # Download UI state
│   │   │   └── AnalyticsViewModel.kt           # Analytics UI state
│   │   ├── ui/
│   │   │   ├── CaptureScreen.kt                # Workflow states 1-5
│   │   │   ├── CaptureScreenPart2.kt           # Workflow states 6-9
│   │   │   ├── Screens.kt                      # Navigation & screens
│   │   │   ├── screens/
│   │   │   │   ├── ModelDownloadScreen.kt      # Model download UI
│   │   │   │   └── AnalyticsStatsScreen.kt     # Analytics stats UI
│   │   │   ├── components/
│   │   │   │   └── GemmaModelDownloadButton.kt # Download button
│   │   │   └── theme/                          # Material Design 3
│   │   ├── utils/
│   │   │   ├── LlmService.kt                   # Gemma 3N integration
│   │   │   ├── LibWhisper.kt                   # Whisper JNI wrapper
│   │   │   ├── SpeechRecognitionHelper.kt      # Audio recording
│   │   │   ├── InferenceMetricsCollector.kt    # Per-inference metrics
│   │   │   ├── ResourceMonitor.kt              # CPU/memory monitoring
│   │   │   ├── DeviceInfoCollector.kt          # Hardware detection
│   │   │   └── ErrorClassifier.kt              # Error categorization
│   │   ├── MainActivity.kt
│   │   └── WhereIKeptApplication.kt
│   ├── cpp/
│   │   ├── whisper.cpp/                        # Git submodule
│   │   ├── whisper_jni.cpp                     # JNI bridge
│   │   └── CMakeLists.txt                      # Native build
│   ├── res/                                    # Resources
│   └── AndroidManifest.xml
├── build.gradle.kts
├── README.md
├── CHANGELOG.md
└── ARCHITECTURE.md                             # This file
```

## Concurrency Model

- **Audio Recording**: Background coroutine on `Dispatchers.IO`
- **Whisper Transcription**: `Dispatchers.IO`, runs in parallel while user captures image
- **Gemma Analysis**: `Dispatchers.IO`, streaming via Kotlin Flow
- **Database Operations**: Suspended functions on `Dispatchers.IO`
- **UI State**: `StateFlow` collected on `Dispatchers.Main`

## Model Setup

### Whisper Model
```bash
# Download
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin -O app/src/main/assets/ggml-tiny.en.bin

# Alternative models (better accuracy, larger size)
# ggml-base.en.bin (142 MB)
# ggml-small.en-q5_1.bin (188 MB)
```

### Gemma 3N Model

**Option A: In-App Download (Recommended)**
- App shows download screen on first launch
- OAuth sign-in to HuggingFace (one-time)
- Download starts automatically (~3 GB)

**Option B: Manual Download**
```bash
# Download from HuggingFace
# https://huggingface.co/google/gemma-3n-E2B-it-litert-lm

# Push to device
adb push gemma-3n-e2b-it-int4.litertlm /sdcard/Android/data/com.whereikept.app/files/models/
```

## Build Configuration

- **Languages**: Kotlin + C++ (JNI/NDK for Whisper)
- **Min SDK**: API 26 (Android 8.0)
- **Target SDK**: API 35 (Android 15)
- **Build Tools**: Gradle 8.13.2, Kotlin 2.1.0, CMake 3.22.1
- **NDK ABIs**: arm64-v8a, armeabi-v7a, x86, x86_64
- **C++ Standard**: C++17
- **noCompress assets**: `.task`, `.bin`, `.tflite`, `.litertlm`

## Architecture Decisions

### Why Single ViewModel for Capture?
Single source of truth for state management. Easier to handle transitions between 9 workflow steps without state-sharing issues between multiple ViewModels.

### Why StateFlow over LiveData?
Better Compose integration with `collectAsState()`, more Kotlin-idiomatic, and cleaner syntax.

### Why Separate CaptureScreen + CaptureScreenPart2?
Single file exceeded 1000 lines. Logical split: main workflow states in CaptureScreen, review/editing components in Part2.

### Why Release Whisper After Transcription?
The ~74 MB native model stays in memory if kept loaded. Since users typically record once then review/edit, releasing frees memory. Re-initialization (~2-5s) happens during next recording start, overlapping with recording time.

## Performance

### Model Loading (First Time)
- Whisper: ~2-5 seconds
- Gemma 3N: ~3-8 seconds

### Inference Speed (Pixel 6)
- Whisper transcription: 2-3x real-time (3s audio → 6-9s processing)
- Gemma extraction: 1-4 seconds per transcription

### Storage
- APK: ~200 MB (without models)
- Whisper model: 74 MB
- Gemma model: ~3 GB
- Database: ~100 KB per 1000 items
