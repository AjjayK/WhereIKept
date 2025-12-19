# Where I Kept - Android App

A personal memory assistant Android app that helps you remember where you stored your belongings. Record yourself while organizing, and the app will extract and store object-location information for easy retrieval later.

## Features

### 🎤 Voice Recording
- Record yourself while organizing ("I'm putting my keys in the kitchen drawer")
- Automatic speech-to-text transcription using Android's built-in speech recognition
- Real-time partial results display while speaking

### 🤖 LLM-Powered Extraction
- Extracts object-location pairs from transcribed text using a local LLM
- Supports **Ollama** (local) or **OpenAI-compatible** APIs
- Fallback pattern matching when LLM is unavailable

### 🔍 Smart Search
- Full-text search across all stored items
- LLM-generated natural language responses to queries
- Search by object name, location, or description

### 📋 Manual Entry
- Add items manually when voice recording isn't convenient
- Optional notes/description field

### 💾 Local Storage
- All data stored locally using Room database with FTS4 (Full-Text Search)
- No cloud dependency for basic functionality
- Fast, efficient querying

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        UI Layer (Compose)                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │ RecordScreen│  │SearchScreen │  │ ItemsScreen │             │
│  └─────────────┘  └─────────────┘  └─────────────┘             │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────┼───────────────────────────────────┐
│                        ViewModel                                │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                   MainViewModel                          │   │
│  │  - UI State management                                   │   │
│  │  - Coordinates speech recognition, LLM, and database     │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────┼───────────────────────────────────┐
│                        Services                                 │
│  ┌──────────────────┐    ┌──────────────────┐                  │
│  │ SpeechRecognition│    │    LlmService    │                  │
│  │      Helper      │    │  (Ollama/OpenAI) │                  │
│  └──────────────────┘    └──────────────────┘                  │
└─────────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────┼───────────────────────────────────┐
│                        Data Layer                               │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                   Repository                              │  │
│  └──────────────────────────────────────────────────────────┘  │
│                              │                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              Room Database with FTS4                      │  │
│  │  ┌─────────┐  ┌─────────────┐  ┌─────────────┐          │  │
│  │  │  Items  │  │  Recordings │  │   Images    │          │  │
│  │  └─────────┘  └─────────────┘  └─────────────┘          │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Quick Start

### Prerequisites

1. **Android Studio** Arctic Fox or later
2. **Android SDK** 26+ (Android 8.0 Oreo)
3. **Kotlin** 1.9+
4. **Android NDK** (install via SDK Manager for Whisper support)

### Setup Steps

#### 1. Download Required Models

The app uses two AI models that must be downloaded separately (not included due to size):

**Whisper Model (Speech-to-Text)** - Required, ~74 MB:
```bash
cd app/src/main/assets/
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin
```

**Gemma Model (LLM Extraction)** - Required, ~1.5 GB:
1. Go to: https://www.kaggle.com/models/google/gemma/tfLite/gemma-2b-it-gpu-int4
2. Sign in and click "Download"
3. Extract to get `gemma-2b-it-gpu-int4.bin`
4. Copy to `app/src/main/assets/gemma-2b-it-gpu-int4.bin`

Both models will auto-copy to the app on first launch.

#### 2. Build and Run

1. Clone this repository
2. Place both model files in `app/src/main/assets/`
3. Open in Android Studio
4. Sync Gradle files
5. Build and install on device (physical device recommended)
6. First launch takes 1-2 minutes to copy models
7. Grant microphone permission when prompted

### Permissions Required

- **RECORD_AUDIO**: Voice recording and Whisper transcription
- **CAMERA**: Image capture of storage locations
- **INTERNET**: Optional (only for future cloud features)

## Usage

### Recording Items

1. Tap the **Record** tab
2. Press the microphone button
3. Speak naturally: *"I'm putting my passport in the bedroom closet, top shelf"*
4. Press stop or wait for automatic detection
5. The app extracts and saves: **passport** → **bedroom closet, top shelf**

### Finding Items

1. Tap the **Find** tab
2. Type or speak your query: *"Where are my keys?"*
3. View results and LLM-generated response

### Managing Items

1. Tap the **Items** tab
2. View all stored items
3. Delete items by tapping the trash icon
4. Add items manually with the + button

## How It Works

The app uses two AI models running entirely on-device:

1. **Whisper.cpp** (74 MB) - Converts voice recordings to text with high accuracy
2. **Gemma 2B** (1.5 GB) - Extracts structured data from transcriptions using MediaPipe LLM

When you record: *"I put my passport in the top drawer next to the stapler yesterday"*

The app extracts:
- **Object**: passport
- **Location**: top drawer
- **Nearby**: next to the stapler
- **Time Hint**: yesterday
- **Confidence**: 0.92

Everything runs offline with no internet required.

## Project Structure

```
WhereIKept/
├── app/
│   ├── src/main/
│   │   ├── java/com/whereikept/app/
│   │   │   ├── data/
│   │   │   │   ├── Entities.kt      # Room entities
│   │   │   │   ├── Daos.kt          # Data access objects
│   │   │   │   ├── Database.kt      # Room database
│   │   │   │   └── Repository.kt    # Data repository
│   │   │   ├── ui/
│   │   │   │   ├── Screens.kt       # Compose UI screens
│   │   │   │   └── theme/           # Material 3 theme
│   │   │   ├── viewmodel/
│   │   │   │   └── MainViewModel.kt # Main ViewModel
│   │   │   ├── utils/
│   │   │   │   ├── LlmService.kt    # LLM integration
│   │   │   │   └── SpeechRecognitionHelper.kt
│   │   │   ├── MainActivity.kt
│   │   │   └── WhereIKeptApplication.kt
│   │   ├── res/
│   │   │   ├── values/
│   │   │   │   ├── strings.xml
│   │   │   │   ├── colors.xml
│   │   │   │   └── themes.xml
│   │   │   └── xml/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Future Enhancements

- [ ] **Image capture**: Take photos of storage locations
- [ ] **Image analysis**: Use vision LLM to describe what's in images
- [ ] **Voice queries**: Ask questions using voice
- [ ] **Categories**: Organize items by room or category
- [ ] **Reminders**: Get reminded where things are based on context
- [ ] **Backup/Sync**: Cloud backup and multi-device sync
- [ ] **Widgets**: Home screen widgets for quick recording
- [ ] **Wear OS**: Companion app for smartwatches

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Database**: Room with FTS4
- **Architecture**: MVVM with StateFlow
- **Speech**: Android SpeechRecognizer
- **Networking**: OkHttp
- **JSON**: Gson

## License

MIT License - Feel free to use, modify, and distribute.

## Contributing

Contributions welcome! Please open an issue or pull request.
