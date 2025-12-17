# MediaPipe LLM Setup Guide for WhereIKept

This guide explains how to download and configure the Gemini Nano (Gemma) model for on-device LLM inference.

## Phase 2 Implementation Complete ✅

The following components have been integrated:

1. ✅ **MediaPipe LLM dependencies** added to `build.gradle.kts`
2. ✅ **LlmService.kt** implemented with full JSON extraction logic
3. ✅ **MainViewModel.kt** updated to use LLM instead of regex
4. ✅ **Data models** created for structured extraction
5. ✅ **Prompt engineering** optimized for JSON output
6. ✅ **Error handling** and fallback mechanisms

---

## Model Download Instructions

### Step 1: Choose Your Gemma Model

MediaPipe supports several Gemma model variants. Choose based on your device capabilities:

| Model | Size | Speed | Quality | Recommended For |
|-------|------|-------|---------|-----------------|
| **gemma-2b-it-gpu-int4** | ~1.5 GB | Fast | Good | Most devices (Recommended) |
| gemma-2b-it-cpu-int4 | ~1.5 GB | Medium | Good | CPU-only devices |
| gemma-7b-it-gpu-int4 | ~4 GB | Slow | Best | High-end devices |

### Step 2: Download the Model

**Option A: Using Kaggle (Recommended)**

1. Go to Kaggle Gemma models:
   - Gemma 2B: https://www.kaggle.com/models/google/gemma/tfLite/gemma-2b-it-gpu-int4
   - Gemma 7B: https://www.kaggle.com/models/google/gemma/tfLite/gemma-7b-it-gpu-int4

2. Click "Download" (you may need to sign in)

3. Extract the downloaded file - you'll get a `.bin` file

**Option B: Using Google AI Studio**

1. Visit https://aistudio.google.com/
2. Navigate to "Downloads" or "Models"
3. Download Gemma 2B or 7B for TFLite/MediaPipe

**Option C: Direct Download (if available)**

```bash
# Example using wget (Linux/Mac)
wget https://storage.googleapis.com/download.tensorflow.org/models/tflite/task_library/gemma/gemma-2b-it-gpu-int4.bin
```

### Step 3: Place the Model in Your Android Project

**Method 1: Copy to Device Files (Recommended for Development)**

1. Connect your Android device via USB
2. Enable USB debugging
3. Use ADB to push the model:

```bash
# Navigate to your model download directory
cd ~/Downloads

# Push to app's files directory
adb push gemma-2b-it-gpu-int4.bin /sdcard/Download/

# Then manually move via File Manager on device to:
# /data/data/com.whereikept.app/files/gemma-2b-it-gpu-int4.bin
```

**OR** Use Android Studio Device File Explorer:
- View → Tool Windows → Device File Explorer
- Navigate to `/data/data/com.whereikept.app/files/`
- Right-click → Upload
- Select your `.bin` file

**Method 2: Bundle with APK (For Production)**

⚠️ **Warning**: This will significantly increase your APK size (~1.5 GB)

1. Create `assets` folder if it doesn't exist:
   ```
   app/src/main/assets/
   ```

2. Copy model file to assets:
   ```
   cp gemma-2b-it-gpu-int4.bin app/src/main/assets/
   ```

3. Update `LlmService.kt` to copy from assets on first run (already implemented)

### Step 4: Update Model Name in Code (if different)

If you downloaded a different model variant, update the model name in `LlmService.kt`:

```kotlin
// In LlmService.kt line ~39
companion object {
    private const val TAG = "LlmService"
    private const val MODEL_NAME = "gemma-2b-it-gpu-int4.bin"  // Change this to match your file
}
```

---

## Verification

### Check Model Installation

1. Build and run the app in Android Studio
2. Check Logcat with filter `LlmService`:

**Expected logs:**
```
I/LlmService: ===========================================
I/LlmService: Initializing MediaPipe LLM Service
I/LlmService: ===========================================
I/LlmService: Model found at: /data/data/com.whereikept.app/files/gemma-2b-it-gpu-int4.bin
I/LlmService: Model size: 1489 MB
I/LlmService: Creating LLM inference instance...
I/LlmService: ✓ MediaPipe LLM initialized successfully
I/LlmService: ===========================================
```

**If model not found:**
```
W/LlmService: Model not found at: /data/data/com.whereikept.app/files/gemma-2b-it-gpu-int4.bin
W/LlmService: Please download Gemma model and place it in app files directory
```

### Test Extraction

1. Record a voice message: "I put my keys in the kitchen drawer next to the spoons"
2. Check Logcat for extraction results:

```
I/LlmService: Extracting items from transcription
I/LlmService: Transcription: "I put my keys in the kitchen drawer next to the spoons"
I/LlmService: LLM initialized: true
I/LlmService: Calling LLM inference...
I/LlmService: LLM Response received:
I/LlmService:   Duration: 2341 ms
I/LlmService: ✓ Extracted 1 item(s)
I/LlmService:   Item 1: keys -> kitchen drawer (confidence: 0.95)
```

---

## Troubleshooting

### Issue: "Model not found" error

**Solution**: Ensure model file is in correct location:
```bash
# Check file exists
adb shell ls -lh /data/data/com.whereikept.app/files/

# Should show:
# -rw------- 1 u0_a123 u0_a123 1.4G 2024-01-15 10:30 gemma-2b-it-gpu-int4.bin
```

### Issue: "Out of memory" error

**Solutions**:
1. Use smaller model (gemma-2b instead of gemma-7b)
2. Close other apps before testing
3. Increase maxTokens limit in LlmService.kt:
   ```kotlin
   .setMaxTokens(256)  // Reduce from 512
   ```

### Issue: Slow inference (>10 seconds)

**Solutions**:
1. Ensure using GPU variant (`-gpu-` in filename)
2. Test on physical device (emulator is very slow)
3. Reduce temperature and tokens:
   ```kotlin
   .setMaxTokens(256)
   .setTemperature(0.2f)
   ```

### Issue: JSON parsing errors

**Check logs for**:
```
E/LlmService: JSON parsing error: Expected BEGIN_OBJECT but was STRING
```

**Solutions**:
1. Model may not be instruction-tuned - ensure using `-it-` variant
2. Prompt engineering may need adjustment
3. Try increasing temperature slightly (0.3 → 0.4)

### Issue: Empty extraction results

**Possible causes**:
1. Model not understanding prompt format
2. Transcription quality issues
3. Input text too short or ambiguous

**Debug**:
```kotlin
// Enable debug logging in LlmService.kt
Log.d(TAG, "Full prompt:\n$prompt")
Log.d(TAG, "Raw response:\n$response")
```

---

## Performance Optimization

### For Faster Inference

```kotlin
// In LlmService.kt initialize()
val options = LlmInference.LlmInferenceOptions.builder()
    .setModelPath(modelPath.absolutePath)
    .setMaxTokens(256)        // Reduce tokens
    .setTemperature(0.2f)     // Lower temperature
    .setTopK(20)              // Reduce from 40
    .build()
```

### For Better Quality

```kotlin
val options = LlmInference.LlmInferenceOptions.builder()
    .setModelPath(modelPath.absolutePath)
    .setMaxTokens(512)        // More tokens
    .setTemperature(0.4f)     // Higher temperature
    .setTopK(40)
    .setTopP(0.9f)            // Add top-p sampling
    .build()
```

---

## Alternative: Using Gemini API (Cloud-based)

If on-device LLM is too large or slow, you can use Google's Gemini API instead:

1. Get API key from https://aistudio.google.com/
2. Update `LlmService.kt` to use Retrofit + Gemini API
3. Smaller app size, faster responses, but requires internet

---

## Next Steps

After successful model setup:

1. ✅ Test with various voice inputs
2. ✅ Verify database entries have all fields populated
3. ✅ Check search functionality includes new metadata
4. ⏭️ **Phase 3**: UI enhancements to display confidence, nearby, time_hint
5. ⏭️ **Phase 4**: Advanced features (batch processing, model switching)

---

## Model File Checklist

- [ ] Model downloaded from Kaggle/AI Studio
- [ ] Model file renamed correctly (matches `MODEL_NAME` in code)
- [ ] Model placed in `/data/data/com.whereikept.app/files/`
- [ ] App rebuilt with MediaPipe dependencies
- [ ] Initialization logs show success
- [ ] Test extraction produces JSON results

---

## Support

For MediaPipe LLM documentation:
- https://developers.google.com/mediapipe/solutions/genai/llm_inference

For Gemma model documentation:
- https://ai.google.dev/gemma

For issues:
- Check Logcat with tags: `LlmService`, `MainViewModel`
- Enable verbose logging in `LlmService.kt`
