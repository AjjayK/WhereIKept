# Whisper Speech-to-Text Setup Guide

This app uses **Whisper.cpp** for offline speech-to-text transcription. Follow these steps to set up the Whisper model.

## Important Note

**Model files are NOT included in this repository** due to their large size (74-181 MB). You must download them separately after cloning. Model files are excluded via `.gitignore` to prevent GitHub's file size limits from blocking pushes.

## Prerequisites

1. **Android NDK** must be installed via Android Studio SDK Manager
2. **CMake** 3.22.1 or higher (configured in build.gradle.kts)
3. At least **500 MB** of free space for the Whisper model

## Setup Steps

### 1. Download whisper.cpp

**Recommended: Use shallow clone to save space**

```bash
cd app/src/main/cpp
git clone --depth 1 https://github.com/ggerganov/whisper.cpp.git
```

The `--depth 1` flag downloads only the latest version without full git history (~50 MB instead of ~200+ MB).

**Alternative: If you don't want git history at all, download as zip**

Or download and extract manually from: https://github.com/ggerganov/whisper.cpp/archive/refs/heads/master.zip

### 2. Download the Whisper Model

**REQUIRED:** Download a Whisper model file. The app will not work without it.

Download the **ggml-tiny.en.bin** model (recommended for development):

```bash
# Download from HuggingFace
cd app/src/main/assets/
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin
```

**Model Options:**
- **`ggml-tiny.en.bin` (~74 MB)** - Recommended for development (fast, good accuracy)
- `ggml-base.en.bin` (~142 MB) - Better accuracy, slower
- `ggml-small.en.bin` (~466 MB) - High accuracy, slower
- `ggml-small.en-q5_1.bin` (~188 MB) - Quantized small (good balance)
- `ggml-medium.en-q5_1.bin` (~515 MB) - Highest accuracy, slowest

**Important:** Place the downloaded `.bin` file in `app/src/main/assets/` directory. Do NOT commit these files to git - they are ignored via `.gitignore`.

### 3. Model Loading Strategy

The app automatically loads models from `app/src/main/assets/`:

**At runtime:**
1. App checks if model exists in `context.filesDir`
2. If not found, copies from `assets/` to `context.filesDir`
3. Loads model from `context.filesDir`

**No additional steps needed** - just place the `.bin` file in `app/src/main/assets/` and build the app.

**Alternative Options:**

**Option A: Using adb (for testing)**
```bash
adb push ggml-tiny.en.bin /data/data/com.whereikept.app/files/
```

**Option B: Download at runtime**
Implement a model downloader in the app (requires additional code).

### 4. Verify Installation

The app will log the Whisper initialization status. Check logcat:

```bash
adb logcat | grep -E "(SpeechRecognitionHelper|LibWhisper|WHISPER_JNI)"
```

Expected log output:
```
I/SpeechRecognitionHelper: ===========================================
I/SpeechRecognitionHelper: Initializing SpeechRecognitionHelper
I/SpeechRecognitionHelper: ✓ AudioRecord initialized successfully
I/SpeechRecognitionHelper: Initializing Whisper model...
I/LibWhisper: Whisper native library loaded successfully
I/LibWhisper: Model file found
I/LibWhisper: Model file size: 188 MB
I/WHISPER_JNI: Whisper context initialized successfully
I/SpeechRecognitionHelper: ✓ Whisper model initialized successfully
```

If the model is not found, you'll see:
```
W/SpeechRecognitionHelper: Whisper model not found at: /data/data/com.whereikept.app/files/ggml-small.en-q5_1.bin
W/SpeechRecognitionHelper: Please download model to: /data/data/com.whereikept.app/files/ggml-small.en-q5_1.bin
```

## Build Configuration

The project is already configured with:

- **CMakeLists.txt**: Links whisper.cpp and JNI wrapper
- **build.gradle.kts**: NDK and CMake integration
- **whisper_jni.cpp**: JNI bridge between Kotlin and C++
- **LibWhisper.kt**: Kotlin wrapper with extensive logging

## Transcription Workflow

1. **User taps microphone button** → Recording starts
2. **User speaks** → Audio captured at 16kHz, mono, PCM 16-bit
3. **User taps stop** → Recording stops
4. **Audio processing** → WAV file created
5. **Whisper transcription** → Offline speech-to-text
6. **Result displayed** → Text shown in UI and saved to database

## Logging

Comprehensive logs are generated throughout the workflow:

### Initialization Logs
- AudioRecord setup (sample rate, channels, format)
- Whisper model loading (path, size, status)

### Recording Logs
- Start/stop times
- Recording duration
- Audio chunks captured
- Audio levels

### Transcription Logs
- Audio file details (size, duration)
- Processing time
- Transcription segments
- Final transcription text
- Errors and warnings

### View Logs in Real-Time
```bash
# All speech recognition logs
adb logcat | grep SpeechRecognitionHelper

# Whisper-specific logs
adb logcat | grep -E "(LibWhisper|WHISPER_JNI)"

# Full workflow
adb logcat | grep -E "(SpeechRecognitionHelper|LibWhisper|WHISPER_JNI)"
```

## Troubleshooting

### Issue: "Whisper native library not found"
**Solution**: Build the app with NDK. Make sure Android Studio has NDK installed:
- `Tools > SDK Manager > SDK Tools > NDK (Side by side)`

### Issue: "Model file does not exist"
**Solution**: Verify the model file is in the correct location:
```bash
adb shell ls -lh /data/data/com.whereikept.app/files/
```

### Issue: "Transcription is empty"
**Possible causes**:
- Audio is too quiet → Speak louder or closer to the microphone
- Model not optimized for your language → Use English
- Recording too short → Record at least 1-2 seconds

### Issue: Build fails with CMake errors
**Solution**:
1. Ensure whisper.cpp is cloned in `app/src/main/cpp/whisper.cpp`
2. Check CMakeLists.txt path is correct
3. Clean and rebuild: `Build > Clean Project` then `Build > Rebuild Project`

## Performance

- **Tiny model**: ~2-3x real-time (3 seconds audio → 6-9 seconds processing)
- **Small model**: ~5-8x real-time (3 seconds audio → 15-24 seconds processing)
- **Medium model**: ~10-15x real-time (3 seconds audio → 30-45 seconds processing)

Performance varies by device CPU. Uses 4 threads for optimal balance between speed and battery.

## Model Downloads

Official models: https://huggingface.co/ggerganov/whisper.cpp/tree/main

```bash
# Tiny (32 MB)
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en-q5_1.bin

# Base (58 MB)
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en-q5_1.bin

# Small (188 MB) - Recommended
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small.en-q5_1.bin

# Medium (515 MB)
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-medium.en-q5_1.bin
```

## Next Steps

Once the model is set up:
1. Build and run the app
2. Grant microphone permission
3. Tap the microphone button to start recording
4. Speak clearly
5. Tap stop
6. View the transcription and check logs for detailed information

The transcribed text will automatically be saved to the database via `RecordingEntity`.
