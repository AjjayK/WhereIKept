# WhereIKept - Development Guide

## Implementation Summary

This app uses on-device AI for voice recording and intelligent extraction of object-location pairs.

## Architecture

### Core Components

1. **Speech Recognition** ([SpeechRecognitionHelper.kt](app/src/main/java/com/whereikept/app/utils/SpeechRecognitionHelper.kt))
   - Whisper.cpp integration via JNI
   - 16kHz mono PCM audio recording
   - Model: `ggml-tiny.en.bin` (74 MB)
   - Auto-copies from assets on first launch

2. **LLM Service** ([LlmService.kt](app/src/main/java/com/whereikept/app/utils/LlmService.kt))
   - MediaPipe LLM (Gemma 2B IT GPU INT4)
   - Structured JSON extraction with prompt engineering
   - Model: `gemma-2b-it-gpu-int4.bin` (1.5 GB)
   - Auto-copies from assets on first launch

3. **Data Layer** ([data/](app/src/main/java/com/whereikept/app/data/))
   - Room database with FTS4 full-text search
   - Entities: ItemEntity, RecordingEntity, ImageEntity
   - Repository pattern for data access

4. **UI Layer** ([ui/Screens.kt](app/src/main/java/com/whereikept/app/ui/Screens.kt))
   - Jetpack Compose with Material 3
   - MVVM architecture with StateFlow
   - Three main screens: Record, Find, Items

## Database Schema

### ItemEntity (Version 2)

```kotlin
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val objectName: String,           // e.g., "passport"
    val location: String,              // e.g., "top drawer"
    val description: String = "",      // Additional notes
    val nearby: String? = null,        // e.g., "next to the stapler"
    val timeHint: String? = null,      // e.g., "yesterday"
    val confidence: Float? = null,     // 0.0-1.0 extraction confidence
    val evidence: String? = null,      // Original transcription snippet
    val imagePath: String? = null,     // Path to captured image
    val timestamp: Long = System.currentTimeMillis(),
    val sourceType: String = "voice"   // "voice" or "manual"
)
```

## Model Setup

### Whisper Model

**Purpose**: Speech-to-text transcription
**Size**: 74 MB
**Location**: `app/src/main/assets/ggml-tiny.en.bin`

Download:
```bash
wget https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin -O app/src/main/assets/ggml-tiny.en.bin
```

Alternative models:
- `ggml-base.en.bin` (142 MB) - Better accuracy
- `ggml-small.en-q5_1.bin` (188 MB) - High accuracy

### Gemma Model

**Purpose**: Structured data extraction from transcriptions
**Size**: 1.5 GB
**Location**: `app/src/main/assets/gemma-2b-it-gpu-int4.bin`

Download:
1. Visit: https://www.kaggle.com/models/google/gemma/tfLite/gemma-2b-it-gpu-int4
2. Sign in and download
3. Extract `.bin` file
4. Place in `app/src/main/assets/`

## LLM Extraction Flow

### Input
```
Transcription: "I put my passport in the top drawer next to the stapler yesterday"
```

### Prompt Engineering
```kotlin
val prompt = """
You are a JSON extraction assistant. Extract objects and locations from the text.

Output format (strict JSON):
{
  "items": [
    {
      "object": "item name",
      "location": "where it is",
      "nearby": "nearby reference or null",
      "time_hint": "temporal context or null",
      "confidence": 0.0-1.0,
      "evidence": "exact quote from text"
    }
  ]
}

Text: "$transcription"
"""
```

### Output
```json
{
  "items": [
    {
      "object": "passport",
      "location": "top drawer",
      "nearby": "next to the stapler",
      "time_hint": "yesterday",
      "confidence": 0.92,
      "evidence": "I put my passport in the top drawer next to the stapler yesterday"
    }
  ]
}
```

## Build Configuration

### Dependencies

```kotlin
// MediaPipe LLM
implementation("com.google.mediapipe:tasks-genai:0.10.14")

// Room Database
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// Compose UI
implementation(platform("androidx.compose:compose-bom:2024.02.00"))
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.ui:ui-tooling-preview")

// Networking (for future cloud features)
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.google.code.gson:gson:2.10.1")
```

### NDK Configuration

```kotlin
android {
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
}
```

## Testing

### Verify Model Installation

Check Logcat (filter: `LlmService` or `SpeechRecognitionHelper`):

**Expected on first launch:**
```
I/LlmService: Found model in assets, copying to app files...
I/LlmService: Copied 100 MB...
I/LlmService: Copied 200 MB...
...
I/LlmService: ✓ Model copied successfully! Size: 1489 MB, Time: 87.3s
I/LlmService: ✓ MediaPipe LLM initialized successfully
```

**Expected on subsequent launches:**
```
I/LlmService: Model already exists in app files
I/LlmService: ✓ MediaPipe LLM initialized successfully
```

### Test Extraction

1. Record: *"I put my keys in the kitchen drawer"*
2. Check Logcat:
```
I/LlmService: Extracting items from transcription
I/LlmService: ✓ Extracted 1 item(s)
I/LlmService:   Item 1: keys -> kitchen drawer (confidence: 0.95)
```

## Performance

### Model Loading Times
- **Whisper**: ~2-5 seconds (first time), instant (cached)
- **Gemma**: ~3-8 seconds (first time), instant (cached)

### Inference Times (on Pixel 6)
- **Whisper transcription**: 2-3x real-time (3 sec audio → 6-9 sec processing)
- **LLM extraction**: 1-4 seconds per transcription

### Storage Requirements
- APK with models: ~1.5 GB
- Device storage: ~1.5 GB (models auto-copy once)
- Database: Grows with usage (~100 KB per 1000 items)

## Troubleshooting

### "Model not found in assets"
→ Ensure `.bin` files are in `app/src/main/assets/` and rebuild

### First launch takes forever
→ Normal! 1.5 GB Gemma model copy takes 1-2 minutes. Watch Logcat for progress.

### Extraction returns empty results
→ Check Logcat for JSON parsing errors. Model may need clearer/longer input.

### Build fails with APK size error
→ This is expected with 1.5 GB model. For production, consider on-demand download.

### "Whisper native library not found"
→ Install Android NDK via SDK Manager: Tools → SDK Manager → SDK Tools → NDK

## Code Style

- **Language**: Kotlin only
- **UI**: Jetpack Compose with Material 3
- **Async**: Kotlin Coroutines (viewModelScope)
- **Database**: Room with Flow
- **Architecture**: MVVM with Repository pattern
- **State**: StateFlow for UI state management

## Future Enhancements

- [ ] Display confidence, nearby, time_hint in UI
- [ ] Voice-based search queries
- [ ] Batch processing multiple recordings
- [ ] Model size selection (2B vs 7B)
- [ ] Multi-language support
- [ ] Export/import functionality
- [ ] Widgets for quick recording

## References

- MediaPipe LLM: https://developers.google.com/mediapipe/solutions/genai/llm_inference
- Gemma Models: https://ai.google.dev/gemma
- Whisper.cpp: https://github.com/ggerganov/whisper.cpp
- Room Database: https://developer.android.com/training/data-storage/room
- Jetpack Compose: https://developer.android.com/jetpack/compose
