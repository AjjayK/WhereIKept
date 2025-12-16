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

## Setup

### Prerequisites

1. **Android Studio** Arctic Fox or later
2. **Android SDK** 26+ (Android 8.0 Oreo)
3. **Kotlin** 1.9+

### LLM Setup (Optional but Recommended)

For the best experience, set up a local LLM using Ollama:

1. Install Ollama: https://ollama.ai/
2. Pull a model:
   ```bash
   ollama pull llama3.2
   ```
3. Start Ollama server (runs on port 11434 by default)

The app will work without an LLM using basic pattern matching, but results will be better with one.

### Building the App

1. Clone or download this project
2. **Download Whisper model** (required):
   ```bash
   cd app/src/main/assets/
   wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin
   ```
   See [WHISPER_SETUP.md](WHISPER_SETUP.md) for detailed instructions and model options.

   **Note:** Model files are NOT included in the repository due to their large size.

3. Open in Android Studio
4. Sync Gradle files
5. Run on device or emulator

```bash
./gradlew assembleDebug
```

### Permissions Required

- **RECORD_AUDIO**: For voice recording and transcription
- **CAMERA**: For capturing images of stored items (future feature)
- **INTERNET**: For communicating with local/cloud LLM

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

## Configuration

### LLM Settings

Access settings via the gear icon:

- **API Type**: Ollama or OpenAI-compatible
- **API URL**: Default `http://localhost:11434` for Ollama
- **Model**: Default `llama3.2`

### Supported LLM Endpoints

| Provider | URL Format | Notes |
|----------|------------|-------|
| Ollama | `http://localhost:11434` | Local, free |
| LM Studio | `http://localhost:1234` | Local, free |
| OpenAI | `https://api.openai.com` | Requires API key (modify code) |
| Any OpenAI-compatible | `http://your-server:port` | Self-hosted options |

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
