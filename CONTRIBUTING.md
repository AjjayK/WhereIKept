# Contributing to WhereIKept

Thanks for your interest in contributing! This guide will help you get set up and start building.

## Beta Testing

Not a developer? You can still help by testing the app and reporting bugs!

1. **Join the Google Group**: https://groups.google.com/u/4/g/where-i-kept-testers
2. **Install from Google Play**: https://play.google.com/store/apps/details?id=com.whereikept.app

You must join the group first, then the Play Store link will let you install the app.

## Development Setup

### Prerequisites

- **Android Studio** Hedgehog or later
- **Android NDK** (SDK Manager → SDK Tools → NDK)
- **CMake 3.22.1** (SDK Manager → SDK Tools → CMake)
- **Physical Android device** with 6+ GB RAM (emulators work but AI inference is very slow)

### Build

1. **Clone the repo**
   ```bash
   git clone https://github.com/AjjayK/WhereIKept.git
   cd WhereIKept
   ```

2. **Open in Android Studio** and let Gradle sync

3. **Build the app**
   ```bash
   ./gradlew :app:installDebug
   ```

   The app builds without `google-services.json` — Firebase analytics is optional and falls back to local-only storage.

### Model Setup

The app needs two AI models to function:

**Whisper (speech-to-text, ~74 MB)**

Download `ggml-tiny.en.bin` from [HuggingFace](https://huggingface.co/ggerganov/whisper.cpp/tree/main) and place it in assets:
```bash
cp ggml-tiny.en.bin app/src/main/assets/
```

**Gemma 3N E2B (AI extraction, ~3 GB)**

Download from [HuggingFace](https://huggingface.co/google/gemma-3n-E2B-it-litert-lm) and push to your device:
```bash
adb push gemma-3n-e2b-it-int4.litertlm /sdcard/Android/data/com.whereikept.app/files/models/
```

Alternatively, use the in-app download (requires a HuggingFace account).

## Architecture Overview

The app follows MVVM with a Repository pattern:

```
UI (Compose) → ViewModel (StateFlow) → Service/Repository → Room DB
```

Key components:
- **SpeechRecognitionHelper** — Audio recording + Whisper JNI transcription
- **LlmService** — Gemma 3N inference via LiteRT-LM
- **CaptureViewModel** — 9-state capture workflow state machine
- **MainViewModel** — Search and items list

See [ARCHITECTURE.md](ARCHITECTURE.md) for full details.

## Making Changes

1. **Fork** the repository
2. **Create a branch** from `master`
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Make your changes** — keep commits focused and atomic
4. **Test on a physical device** — AI features don't work well on emulators
5. **Submit a pull request** against `master`

## What to Work On

Check the [Issues](https://github.com/AjjayK/WhereIKept/issues) tab for open issues. Issues labeled `good first issue` are a great starting point.

## Code Style

- Follow existing Kotlin conventions in the codebase
- Use meaningful variable and function names
- Keep functions focused and small

## Testing

Before submitting a PR, make sure all existing test cases in [TESTING_GUIDE.md](TESTING_GUIDE.md) still pass. If your change adds new functionality, add corresponding test cases to your PR description.

## Debugging

Useful Logcat filters:
- `WhereIKept` — General app logs
- `Whisper` — Transcription logs
- `LlmService` — Gemma inference logs
- `CaptureVM` — Capture workflow state transitions

## Questions?

Open an issue and we're happy to help!
