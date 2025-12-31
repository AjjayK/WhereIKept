# WhereIKept - Personal Memory Assistant

A privacy-focused Android app that helps you remember where you stored your belongings using voice recording, image capture, and on-device AI.

## What It Does

WhereIKept lets you record yourself while organizing items ("I'm putting my keys in the kitchen drawer"), captures a photo of the location, and automatically extracts object-location pairs using AI. Later, you can search to find where you stored anything.

**Key Principle**: All AI processing happens on your device. No cloud dependency, no internet required (except for initial model download).

## Features

### Enhanced Capture Workflow
- **Voice Recording** with real-time audio visualization and duration tracking
- **Speech-to-Text** using Whisper.cpp (tiny model, 74 MB) running entirely on-device
- **Image Capture** of storage locations with camera integration
- **AI-Powered Extraction** using Gemma 3N E2B (~3 GB) - Google's latest edge-optimized multimodal LLM
- **Interactive Tag Editing** with draggable overlays on captured images
- **Manual Tag Entry** as fallback or for manual additions

### Smart Search
- **Full-Text Search** powered by Room FTS4 (Full-Text Search)
- **Natural Language Queries** with LLM-generated responses
- Search by object name, location, description, or attributes

### Model Download System
- **In-App Download** of Gemma 3N model from HuggingFace
- **OAuth Authentication** for gated models (via AppAuth)
- **Progress Tracking** with speed, percentage, and ETA
- **Resume Capability** for interrupted downloads
- **Background Download** with WorkManager and foreground service notifications

### Privacy & Offline-First
- All AI inference runs **on-device** (Whisper + Gemma 3N)
- Voice recordings processed locally
- Data stored in local Room database
- No telemetry or cloud sync
- Internet only needed for initial model download

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer (Compose)                    │
│  • CaptureScreen (9-state workflow)                     │
│  • SearchScreen, ItemsListScreen, SettingsScreen        │
│  • MainScreen (tab navigation)                          │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                  ViewModel Layer (MVVM)                  │
│  • CaptureViewModel (state machine)                     │
│  • MainViewModel (search & items)                       │
│  • GemmaDownloadViewModel (model download)              │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                   Service/Util Layer                     │
│  • SpeechRecognitionHelper (Whisper JNI)                │
│  • LlmService (Gemma 3N via LiteRT-LM)                  │
│  • HuggingFaceAuthHelper (OAuth)                        │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                  Repository Layer                        │
│  • WhereIKeptRepository (data operations)               │
│  • GemmaDownloadRepository (download management)        │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│                   Data Layer (Room)                      │
│  • ItemEntity, RecordingEntity, ImageEntity             │
│  • ItemFts (full-text search)                           │
│  • DAOs: ItemDao, RecordingDao, ImageDao                │
└─────────────────────────────────────────────────────────┘
```

## Capture Workflow (9 States)

The app uses a state machine to guide users through the capture process:

```
IDLE → RECORDING → IMAGE_CAPTURE → TRANSCRIBING → ANALYZING
  → REVIEW_EDITING → SUBMITTING → SUCCESS → [back to IDLE]
                                      ↓
                                   ERROR (with retry/skip)
```

1. **IDLE**: Start recording button
2. **RECORDING**: Audio capture with visualization (Whisper runs on stop)
3. **IMAGE_CAPTURE**: Take photo or skip (transcription runs in parallel)
4. **TRANSCRIBING**: Whisper speech-to-text (if not complete)
5. **ANALYZING**: Gemma 3N multimodal analysis (text + image)
6. **REVIEW_EDITING**: Draggable tags on image, manual editing
7. **SUBMITTING**: Save to database
8. **SUCCESS**: Confirmation with 3s auto-reset
9. **ERROR**: Error handling with retry/skip options

## Tech Stack

### Languages & Frameworks
- **Kotlin** - Primary language
- **Jetpack Compose** - Modern UI toolkit with Material Design 3
- **C++** - Whisper.cpp integration via JNI/NDK

### Core Libraries
- **Architecture**: MVVM with Repository pattern
- **Async**: Kotlin Coroutines & StateFlow
- **Database**: Room 2.6.1 with FTS4 full-text search
- **Navigation**: Compose Navigation
- **Camera**: CameraX
- **Image Loading**: Coil Compose

### AI/ML Stack
- **Speech Recognition**: Whisper.cpp (ggml-tiny.en.bin, 74 MB)
  - 16kHz mono PCM audio
  - C++ native library via JNI
  - 2-3x real-time transcription speed

- **LLM Inference**: LiteRT-LM 0.9.0-alpha01 (Google AI Edge)
  - Model: Gemma 3N E2B INT4 (~3 GB)
  - Multimodal support (text + images)
  - Backend auto-selection (GPU/CPU/NPU)
  - Entirely on-device

### Download & Auth
- **WorkManager** - Background model downloads
- **AppAuth** - OAuth for HuggingFace gated models
- **Gson** - JSON parsing

### Platform
- **Min SDK**: API 26 (Android 8.0 Oreo)
- **Target SDK**: API 35 (Android 15)
- **Build**: Gradle 8.13.2, Kotlin 2.1.0, CMake 3.22.1

## Quick Start

### Prerequisites
1. Android Studio Hedgehog or later
2. Android device/emulator with API 26+ (Android 8.0+)
3. Physical device recommended for best performance

### Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd WhereIKept
   ```

2. **Model Setup (Two Options)**

   **Option A: In-App Download (Recommended)**
   - Build and run the app
   - On first launch, you'll see the model download screen
   - Sign in to HuggingFace (one-time OAuth)
   - Download starts automatically (~3 GB)
   - App ready when download completes

   **Option B: Manual Download**
   - Download Gemma 3N E2B model from [HuggingFace](https://huggingface.co/google/gemma-3n-E2B-it-litert-lm)
   - Push to device:
     ```bash
     adb push gemma-3n-e2b-it-int4.litertlm /sdcard/Android/data/com.whereikept.app/files/models/
     ```

3. **Build and Run**
   ```bash
   ./gradlew :app:installDebug
   ```
   Or use Android Studio: **Build → Make Project**

4. **Grant Permissions**
   - Microphone (for voice recording)
   - Camera (for image capture)
   - Storage (auto-granted for app-specific directories)

### First Run
- First launch takes 1-2 minutes if copying model from assets
- Grant microphone and camera permissions when prompted
- Model initialization shows progress in logs

## Usage

### Recording Items
1. Open the **Capture** tab
2. Tap **Start Recording**
3. Speak naturally: *"I'm putting my passport in the bedroom closet, top shelf"*
4. Tap **Stop Recording**
5. **Capture Image** of the location or skip
6. Review and edit extracted tags (drag onto image)
7. Tap **Save & Submit**

### Finding Items
1. Open the **Find** tab
2. Type or speak your query: *"Where are my keys?"*
3. View results with AI-generated natural language response

### Managing Items
1. Open the **Items** tab
2. View all stored items
3. Delete items with trash icon
4. Add items manually with + button

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
│   │   │   └── GemmaModel.kt                   # Model config
│   │   ├── repository/
│   │   │   └── GemmaDownloadRepository.kt      # Download management
│   │   ├── worker/
│   │   │   └── GemmaDownloadWorker.kt          # Background downloads
│   │   ├── viewmodel/
│   │   │   ├── CaptureViewModel.kt             # Capture state machine
│   │   │   ├── CaptureModels.kt                # Capture data models
│   │   │   ├── MainViewModel.kt                # Search & items
│   │   │   └── GemmaDownloadViewModel.kt       # Download UI state
│   │   ├── ui/
│   │   │   ├── CaptureScreen.kt                # Workflow states 1-5
│   │   │   ├── CaptureScreenPart2.kt           # Workflow states 6-9
│   │   │   ├── Screens.kt                      # Navigation & legacy screens
│   │   │   ├── screens/
│   │   │   │   └── ModelDownloadScreen.kt      # Model download UI
│   │   │   ├── components/
│   │   │   │   └── GemmaModelDownloadButton.kt # Download button
│   │   │   └── theme/                          # Material Design 3
│   │   ├── utils/
│   │   │   ├── LlmService.kt                   # Gemma 3N integration
│   │   │   ├── LibWhisper.kt                   # Whisper JNI wrapper
│   │   │   └── SpeechRecognitionHelper.kt      # Audio recording
│   │   ├── MainActivity.kt
│   │   └── WhereIKeptApplication.kt
│   ├── cpp/
│   │   ├── whisper.cpp/                        # Git submodule
│   │   ├── whisper_jni.cpp                     # JNI bridge
│   │   └── CMakeLists.txt                      # Native build
│   ├── res/                                    # Resources
│   └── AndroidManifest.xml
├── build.gradle.kts
├── README.md                                   # This file
├── DEVELOPMENT.md                              # Developer guide
├── IMPLEMENTATION_SUMMARY.md                   # Enhanced workflow details
├── QUICK_START_GUIDE.md                        # Testing guide
└── GEMMA_DOWNLOAD_IMPLEMENTATION.md            # Download feature docs
```

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

## How It Works

### Voice Recording → Transcription
1. User records voice: "I put my passport in the bedroom closet"
2. Audio saved as 16kHz mono PCM WAV file
3. Whisper.cpp transcribes to text (runs in background while user captures image)

### Multimodal AI Extraction
4. Gemma 3N receives both transcript + captured image
5. LLM extracts structured data:
   ```json
   {
     "items": [
       {
         "object": "passport",
         "location": "bedroom closet",
         "object_attribute": "travel document",
         "location_parent": "home"
       }
     ]
   }
   ```

### Interactive Review
6. Tags displayed as draggable chips on image
7. User can:
   - Drag tags to position them on the image
   - Edit tag text
   - Add new tags manually
   - Delete incorrect tags

### Database Storage
8. Each tag saved as ItemEntity
9. Full-text search index updated (FTS4)
10. Image and recording metadata preserved

## Performance

### Model Loading (First Time)
- Whisper: ~2-5 seconds
- Gemma 3N: ~3-8 seconds

### Inference Speed (Pixel 6)
- Whisper transcription: 2-3x real-time (3s audio → 6-9s processing)
- Gemma extraction: 1-4 seconds per transcription
- Gemma performance:
  - GPU: 23.3 tokens/sec (OpenCL)
  - CPU: 17.6 tokens/sec (XNNPACK)
  - NPU: 50-80+ tokens/sec (Qualcomm QNN, if available)

### Storage
- APK: ~200 MB (without models)
- Whisper model: 74 MB
- Gemma model: ~3 GB
- Database: Grows with usage (~100 KB per 1000 items)

## Documentation

- **[README.md](README.md)** (this file) - Project overview and quick start
- **[DEVELOPMENT.md](DEVELOPMENT.md)** - Technical details, model setup, troubleshooting
- **[IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)** - Enhanced workflow architecture
- **[QUICK_START_GUIDE.md](QUICK_START_GUIDE.md)** - Testing guide with step-by-step flows
- **[GEMMA_DOWNLOAD_IMPLEMENTATION.md](GEMMA_DOWNLOAD_IMPLEMENTATION.md)** - Model download feature guide

## Troubleshooting

### "Model NOT FOUND" Error
- Ensure Gemma model is downloaded via in-app download
- Or manually push model to device storage
- Check logs for model initialization status

### Whisper Transcription Fails
- Verify Android NDK is installed (SDK Manager → SDK Tools → NDK)
- Check microphone permission granted
- Ensure audio recording isn't too short (< 1 second)

### Camera Not Working
- Grant camera permission in Settings
- Check FileProvider configuration in AndroidManifest.xml
- Verify external storage is accessible

### LLM Extraction Returns Empty
- Check Logcat for JSON parsing errors
- Ensure transcript has clear object-location phrases
- Try manual tag entry as fallback

### Download Fails
- Check internet connection
- Verify HuggingFace OAuth is configured (see GEMMA_DOWNLOAD_IMPLEMENTATION.md)
- Try canceling and restarting download

For detailed troubleshooting, see [DEVELOPMENT.md](DEVELOPMENT.md).

## Privacy

WhereIKept is designed with privacy as a core principle:

- All AI processing runs on your device
- No data sent to cloud servers
- No telemetry or analytics
- Voice recordings and images stored locally
- Internet only used for initial model download
- No account required (except HuggingFace for model download)

## Requirements

- Android 8.0 (API 26) or higher
- 4+ GB storage space (for AI models)
- 2+ GB RAM recommended
- Microphone for voice recording
- Camera for image capture

## License

MIT License - See LICENSE file for details

## Contributing

Contributions welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## Acknowledgments

- [Whisper.cpp](https://github.com/ggerganov/whisper.cpp) by Georgi Gerganov - On-device speech recognition
- [Google Gemma](https://ai.google.dev/gemma) - Lightweight LLM for edge devices
- [LiteRT-LM](https://github.com/google-ai-edge/litert-lm) - Google AI Edge inference framework
- [AppAuth-Android](https://github.com/openid/AppAuth-Android) - OAuth library

## Support

For issues, questions, or feature requests:
- Check existing documentation files
- Review Logcat logs for errors
- Open an issue on GitHub

---

**Built with privacy and offline-first principles. Your data never leaves your device.**
