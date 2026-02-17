# WhereIKept - Testing Guide

## Prerequisites

1. **Android Studio** Hedgehog or later
2. **Android Device** with API 26+ (physical device recommended)
3. **Models on device:**
   - **Gemma 3N E2B**: Download from [HuggingFace](https://huggingface.co/google/gemma-3n-E2B-it-litert-lm)
     ```bash
     adb push gemma-3n-e2b-it-int4.litertlm /sdcard/Android/data/com.whereikept.app/files/models/
     ```
   - **Whisper Tiny**: Bundled in assets (auto-copied on first launch)

## Build & Run

```bash
# Build native libraries (Whisper) + install APK
./gradlew :app:installDebug

# Or use Android Studio: Build → Make Project
```

### Grant Permissions
```bash
adb shell pm grant com.whereikept.app android.permission.RECORD_AUDIO
adb shell pm grant com.whereikept.app android.permission.CAMERA
```

## Test Cases

### Test 1: Basic Capture Flow (With Image)

1. Launch app → Tap "Capture" tab
2. Tap **Start Recording** → Say: "I'm putting my keys in the kitchen drawer next to the spoons"
3. Tap **Stop Recording** → State changes to "Capture Image"
4. Tap **Capture Image** → Take photo → "Analyzing with Gemma 3N..." screen
5. **Review Tags** → Check extracted tags: "keys → kitchen drawer"
6. Drag tag onto image, edit if needed
7. Tap **Save & Submit** → "Success! Saved 1 item(s)"
8. Switch to **Items** tab → Confirm item appears

**Expected:** Item saved successfully with image reference

### Test 2: Skip Image Flow (Text-Only)

1. Tap **Start Recording** → Say: "My wallet is on the table"
2. Tap **Stop Recording**
3. Tap **Skip Image** → Analysis proceeds with text only
4. Review tags → "wallet → table"
5. Save

**Expected:** Item saved without image

### Test 3: Manual Tag Entry

1. Record silence or speak something unclear
2. Skip Image
3. Empty tags screen → Tap **+ Add**
4. Enter: "phone → desk" → Tap **Add**
5. Save

**Expected:** Manually added tag saved

### Test 4: Error Handling

1. Deny microphone permission → Verify permission error
2. Record for < 1 second → May get "no speech detected"
3. Test retry/skip buttons on error screens

### Test 5: Model Download (First-Time Setup)

1. Build and install app without models
2. Launch → ModelDownloadScreen appears
3. Tap **Download AI Model** → HuggingFace OAuth browser opens
4. Sign in and accept Gemma license → Redirects back to app
5. Download starts → Progress bar with speed/ETA
6. After completion → Navigates to main screen
7. Next launch → Skips download screen

### Test 6: Download Resume

1. Start download
2. Force-close app mid-download
3. Reopen → Tap **Resume Download**
4. Download continues from where it stopped

### Test 7: Analytics Consent

1. First launch → Consent dialog appears
2. Accept → Firebase events sent (verify in Firebase Console DebugView)
3. Decline → No Firebase events
4. Toggle in Settings → State changes correctly

### Test 8: Offline Functionality

1. Enable airplane mode
2. Record, transcribe, analyze, save → All should work
3. Search for saved items → Should work

### Test 9: Search by Object and Location

1. Save an item: "keys → kitchen drawer"
2. Go to **Find** tab → Search "keys" → Item appears
3. Search "kitchen drawer" → Same item appears
4. Search "wallet" → No results (or unrelated items)

**Expected:** Search works by both object name and location

### Test 10: Delete Item

1. Go to **Items** tab
2. Tap trash icon on a saved item → Confirm delete
3. Search for the deleted item → Should not appear

**Expected:** Item removed from database and search index

### Test 11: Multiple Items in One Recording

1. Record: "I'm putting my keys in the drawer and my passport in the bedroom closet"
2. Complete the capture flow
3. Review → Should extract two tag pairs: "keys → drawer" and "passport → bedroom closet"

**Expected:** Multiple object-location pairs extracted from a single recording

### Test 12: Back Button During Capture

1. Start a recording → Press back
2. Start again → Capture image → Press back
3. Verify workflow resets cleanly at each state

**Expected:** No stuck states, clean reset to IDLE

### Test 13: Whisper Memory Release

1. Complete a full capture (record → transcribe → save)
2. Check Logcat for "Whisper model released"
3. Start a new recording → Check Logcat for "re-initializing" message

**Expected:** Whisper model freed after transcription, re-initialized on next recording

### Test 14: Model Not Available

1. Uninstall/remove Gemma model from device
2. Complete recording + transcription
3. Verify error message shown (not a crash)
4. Verify manual tag entry still works as fallback

**Expected:** Graceful error handling when Gemma model is missing

## Edge Cases

- [ ] Record very short audio (< 1 second)
- [ ] Record very long audio (> 60 seconds)
- [ ] Record silence → no speech detected
- [ ] No tags extracted → empty state UI
- [ ] Cancel recording mid-way → verify reset
- [ ] Switch tabs during workflow → verify state preserved
- [ ] App cold start → verify models initialize correctly
- [ ] Low storage scenario
- [ ] Low memory scenario (budget device)

## Debugging

### Logcat Filters

```bash
# All app logs
adb logcat | grep -E "CaptureViewModel|LlmService|SpeechRecognition|LibWhisper"

# By component
adb logcat | grep "CaptureViewModel"     # State transitions, tag management
adb logcat | grep "LlmService"           # Gemma analysis, JSON parsing
adb logcat | grep "SpeechRecognitionHelper"  # Audio recording, Whisper
adb logcat | grep "WHISPER_JNI"          # Native Whisper JNI calls
adb logcat | grep "GemmaDownloadWorker"  # Download progress
```

### Common Issues

| Issue | Cause | Solution |
|-------|-------|----------|
| "Model NOT FOUND" | Gemma model not on device | Push `.litertlm` file via adb or use in-app download |
| "Whisper not initialized" | Whisper model missing | Auto-copied from assets; check logcat for copy errors |
| Crash on image capture | FileProvider issue | Check AndroidManifest.xml FileProvider config |
| Tags not extracting | LLM initialization failed | Check logcat for LlmService errors |
| "Permission denied" | Missing runtime permission | Grant via Settings or adb |
| "Can not open OpenCL library" | GPU not supported | Normal — auto-fallback to CPU backend |
| ".task file error" | Incompatible format | Use `.litertlm` format instead |

### Model Verification

```bash
# Check Gemma model
adb shell ls -lh /sdcard/Android/data/com.whereikept.app/files/models/

# Check Whisper model
adb shell ls -lh /sdcard/Android/data/com.whereikept.app/files/
```

## Performance Tips

1. Use physical device (not emulator) for realistic benchmarks
2. Monitor memory with Android Profiler during inference
3. Check thermal throttling in logcat (thermal_status)
4. Whisper tiny model trades accuracy for speed — acceptable for most speech

## Pre-Release Checklist

- [ ] Test on Android 8.0+ (API 26+)
- [ ] Test on Android 13+ (notification permission)
- [ ] Test with slow network (model download)
- [ ] Test with interruptions (airplane mode, low battery)
- [ ] Test storage full scenario
- [ ] Verify model files are correct sizes after download
- [ ] Test app restart during download
- [ ] Verify no crashes in logcat
- [ ] Verify analytics consent dialog flow
- [ ] Verify offline functionality in airplane mode
