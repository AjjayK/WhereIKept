# WhereIKept - Quick Start Guide

## Phase 1 & 2 Complete! 🎉

Your WhereIKept app now has **LLM-powered extraction** with rich metadata.

---

## What You Need to Do

### 1. Download Gemma Model (Required)

**Easiest Method**:
1. Go to: https://www.kaggle.com/models/google/gemma/tfLite/gemma-2b-it-gpu-int4
2. Click "Download" (sign in if needed)
3. Extract → you'll get `gemma-2b-it-gpu-int4.bin` (~1.5 GB)

### 2. Install Model on Device

**Using Android Studio**:
1. Connect your device via USB
2. View → Tool Windows → **Device File Explorer**
3. Navigate to: `/data/data/com.whereikept.app/files/`
4. Right-click → **Upload**
5. Select `gemma-2b-it-gpu-int4.bin`

**Using ADB**:
```bash
adb push gemma-2b-it-gpu-int4.bin /sdcard/Download/
# Then manually move to app's files directory using File Manager
```

### 3. Build & Run

1. **Sync Project** in Android Studio (Ctrl+Shift+O / Cmd+Shift+O)
2. **Build** → Make Project
3. **Run** on physical device (emulator will be very slow)

### 4. Test It!

1. Open the app
2. Tap **Record** tab
3. Say: *"I put my passport in the top drawer next to the stapler yesterday"*
4. Tap **Stop**
5. Check if item saved with metadata!

---

## Verify It's Working

### Check Logcat

Filter by `LlmService` - you should see:

```
✓ MediaPipe LLM initialized successfully
✓ Extracted 1 item(s)
  Item 1: passport -> top drawer (confidence: 0.92)
```

### Check Database

Go to **Items** tab - you should see your item with:
- Object: "passport"
- Location: "top drawer"
- Nearby: "next to the stapler" *(new!)*
- Time hint: "yesterday" *(new!)*
- Confidence: 0.92 *(new!)*

---

## Troubleshooting

### "Model not found"
→ Double-check model is in `/data/data/com.whereikept.app/files/`

### Build errors
→ Sync project: File → Sync Project with Gradle Files

### App crashes on launch
→ Check Logcat for stack trace, ensure model file isn't corrupted

### Slow extraction (>10 sec)
→ Normal on first run (model loading). Subsequent runs should be 1-4 seconds.

### No items extracted
→ Check Logcat for JSON parsing errors. Model may need more specific input.

---

## Files Changed

✅ `app/build.gradle.kts` - MediaPipe dependencies
✅ `app/src/main/java/.../LlmService.kt` - **NEW FILE**
✅ `app/src/main/java/.../MainViewModel.kt` - Uses LLM now
✅ `app/src/main/java/.../Entities.kt` - Added 4 fields
✅ `app/src/main/java/.../Database.kt` - Version 2
✅ `app/src/main/java/.../Daos.kt` - Search updated

---

## What's Different Now

### Before (Regex)
```
"I put my keys in the drawer"
   ↓
extractItemsSimple() - regex match
   ↓
ItemEntity(object="keys", location="drawer")
```

### After (LLM)
```
"I put my passport in the top drawer next to the stapler yesterday"
   ↓
MediaPipe LLM (Gemma 2B) with prompt engineering
   ↓
JSON: {
  "object": "passport",
  "location": "top drawer",
  "nearby": "next to the stapler",
  "time_hint": "yesterday",
  "confidence": 0.92,
  "evidence": "I put my passport in..."
}
   ↓
ItemEntity with ALL fields populated
```

---

## Next Steps (Optional)

- **Phase 3**: Update UI to show confidence badges, nearby info
- **Phase 4**: Add batch processing, model selection
- **Phase 5**: Implement voice queries for searching

---

## Need Help?

1. Check [MEDIAPIPE_LLM_SETUP.md](MEDIAPIPE_LLM_SETUP.md) - detailed setup
2. Check [PHASE2_IMPLEMENTATION_SUMMARY.md](PHASE2_IMPLEMENTATION_SUMMARY.md) - full technical details
3. Check Logcat tags: `LlmService`, `MainViewModel`, `SpeechRecognitionHelper`

---

**Ready to test? Build → Run → Record → Enjoy! 🚀**
