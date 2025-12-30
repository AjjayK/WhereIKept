# WhereIKept - Quick Start Guide

## Build & Run

### Prerequisites
1. **Android Studio** Hedgehog or later
2. **Android Device/Emulator** with API 26+ (Android 8.0+)
3. **Models** (place in device storage):
   - **Gemma 3N E2B**: `gemma-3n-e2b-it-int4.litertlm`
     - Download from: https://huggingface.co/google/gemma-3n-E2B-it-litert-lm
     - Push to device: `adb push gemma-3n-e2b-it-int4.litertlm /sdcard/Android/data/com.whereikept.app/files/models/`

   - **Whisper Tiny**: `ggml-tiny.en.bin` (optional, for transcription)
     - Download from: https://huggingface.co/ggerganov/whisper.cpp
     - Push to device: `adb push ggml-tiny.en.bin /sdcard/Android/data/com.whereikept.app/files/`

### Build Steps

```bash
# Clone and open in Android Studio
cd "D:\Personal\Where I Kept\WhereIKept"

# Build native libraries (Whisper)
./gradlew :app:externalNativeBuildDebug

# Build and install APK
./gradlew :app:installDebug

# Or use Android Studio: Build → Make Project
```

### Grant Permissions

```bash
# Grant microphone and camera permissions
adb shell pm grant com.whereikept.app android.permission.RECORD_AUDIO
adb shell pm grant com.whereikept.app android.permission.CAMERA
```

## Testing the New Workflow

### Test 1: Basic Capture Flow (With Image)

1. **Launch App** → Tap "Capture" tab (camera icon)
2. **Tap "Start Recording"**
   - Speak clearly: "I'm putting my keys in the kitchen drawer next to the spoons"
   - Observe: Timer running, audio level indicator animating
3. **Tap "Stop Recording"**
   - State changes to "Capture Image"
   - Background: Whisper transcription starts
4. **Tap "Capture Image"**
   - Camera opens
   - Take photo of a drawer/location
   - Observe: "Analyzing with Gemma 3N..." screen
5. **Review Tags**
   - Check extracted tags: "keys → kitchen drawer"
   - Drag tag onto image to position it
   - Tap tag to edit if needed
6. **Tap "Save & Submit"**
   - Observe: "Success! Saved 1 item(s)"
7. **Verify**
   - Switch to "Items" tab
   - Confirm "keys → kitchen drawer" appears

**Expected Result:** ✅ Item saved successfully with image reference

---

### Test 2: Skip Image Flow (Text-Only)

1. **Tap "Start Recording"**
   - Say: "My wallet is on the table"
2. **Tap "Stop Recording"**
3. **Tap "Skip Image"** (instead of capturing)
   - Observe: Transcription continues
   - Observe: Analysis proceeds with text only
4. **Review Tags**
   - Check: "wallet → table" extracted
   - Edit/add tags as needed
5. **Save**

**Expected Result:** ✅ Item saved without image

---

### Test 3: Manual Tag Entry (No Tags Extracted)

1. **Record silence** or speak something unclear
2. **Skip Image**
3. **Empty tags screen appears**
4. **Tap "+ Add"** button
5. **Enter:** "phone → desk"
6. **Tap "Add"**
7. **Save**

**Expected Result:** ✅ Manually added tag saved

---

### Test 4: Error Handling

1. **Deny Microphone Permission** → Observe permission error
2. **Record for 0.5 seconds** → May get "no speech detected"
3. **Network issues during Gemma** → Retry button appears

---

## Debugging

### Check Logs

```bash
# View all app logs
adb logcat | grep -E "CaptureViewModel|LlmService|SpeechRecognition|LibWhisper"

# Filter by component
adb logcat | grep "CaptureViewModel"
```

### Key Log Tags
- `CaptureViewModel` - State transitions, tag management
- `LlmService` - Gemma analysis, JSON parsing
- `SpeechRecognitionHelper` - Audio recording, Whisper transcription
- `LibWhisper` - Native Whisper JNI calls

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| "Model NOT FOUND" | Gemma model not on device | Push .litertlm file via adb |
| "Whisper not initialized" | Whisper model missing | Push ggml-tiny.en.bin or app uses placeholder |
| Crash on image capture | FileProvider issue | Check AndroidManifest.xml FileProvider config |
| Tags not extracting | LLM initialization failed | Check logcat for LlmService errors |
| "Permission denied" | Missing runtime permission | Grant via Settings or adb |

### Model Verification

```bash
# Check if Gemma model exists
adb shell ls -lh /sdcard/Android/data/com.whereikept.app/files/models/

# Check if Whisper model exists
adb shell ls -lh /sdcard/Android/data/com.whereikept.app/files/
```

---

## Architecture Overview

```
User Interaction
    ↓
CaptureScreen (UI)
    ↓
CaptureViewModel (State Machine)
    ↓
┌─────────────────┬──────────────────┬──────────────────┐
│                 │                  │                  │
SpeechRecognition  LlmService       Repository
Helper             (Gemma 3N)       (Room DB)
(Whisper)
│                 │                  │
└─────────────────┴──────────────────┴──────────────────┘
```

## State Machine

```
IDLE
  ↓ [Start Recording]
RECORDING
  ↓ [Stop Recording]
IMAGE_CAPTURE
  ↓ [Capture/Skip Image]
TRANSCRIBING (background Whisper)
  ↓
ANALYZING (Gemma 3N)
  ↓
REVIEW_EDITING (Tag Overlay UI)
  ↓ [Save & Submit]
SUBMITTING (DB write)
  ↓
SUCCESS
  ↓ [3s timeout]
IDLE (reset)
```

## UI States Checklist

- [ ] IDLE - "Start Recording" button visible
- [ ] RECORDING - Timer + audio waveform animating
- [ ] IMAGE_CAPTURE - Camera button + skip option
- [ ] TRANSCRIBING - Loading spinner + "Transcribing..."
- [ ] ANALYZING - Image preview + transcript + "Analyzing..."
- [ ] REVIEW_EDITING - Draggable tags on image
- [ ] SUBMITTING - "Saving items..." spinner
- [ ] SUCCESS - Checkmark + "Saved N items"
- [ ] ERROR - Error icon + retry/cancel buttons

## Performance Tips

1. **Use Tiny Whisper Model** - Faster transcription (30-60s on mobile)
2. **Compress Images** - Resize to 1024x1024 before sending to Gemma
3. **Test on Mid-Range Device** - Pixel 4a or similar
4. **Monitor Memory** - Large images can cause OOM

## Known Limitations (MVP)

1. **No Screenshot Composite** - Image + tags not merged into single image for DB
2. **No Undo/Redo** - Tag edits are immediate
3. **Fixed Tag Position** - Coordinates don't scale across screen sizes
4. **Single-Language** - English only (Whisper + Gemma models)

---

## Next Steps After Testing

1. **Performance Profiling** - Use Android Profiler to check memory/CPU
2. **UI Polish** - Add animations, loading skeletons
3. **Error Messages** - More specific error descriptions
4. **Accessibility** - Add content descriptions for TalkBack
5. **Unit Tests** - ViewModel state transitions
6. **Integration Tests** - LLM service mocking

---

## File Structure Reference

```
app/src/main/java/com/whereikept/app/
├── viewmodel/
│   ├── CaptureViewModel.kt        # State machine logic
│   ├── CaptureModels.kt           # Data classes
│   └── MainViewModel.kt           # Existing (Search/Items tabs)
├── ui/
│   ├── CaptureScreen.kt           # Main workflow UI (states 1-5)
│   ├── CaptureScreenPart2.kt      # Review/Edit UI (states 6-9)
│   └── Screens.kt                 # Navigation + old screens
├── utils/
│   ├── LlmService.kt              # Gemma 3N integration (ENHANCED)
│   ├── LibWhisper.kt              # Whisper JNI wrapper
│   └── SpeechRecognitionHelper.kt # Audio recording
├── data/
│   ├── Entities.kt                # Room entities
│   ├── Daos.kt                    # Database access
│   ├── Repository.kt              # Data layer
│   └── Database.kt                # Room database
└── MainActivity.kt                # Entry point
```

---

## Support

For issues or questions:
1. Check `IMPLEMENTATION_SUMMARY.md` for detailed architecture
2. Review logcat output for error details
3. Verify models are correctly placed on device
4. Ensure all permissions granted

Happy testing! 🚀
