# Quick Testing Guide - Gemma Model Download

## ✅ Setup Complete!

All integration steps are done:
1. ✅ OAuth Client ID added to `HuggingFaceAuthHelper.kt`
2. ✅ OAuth redirect URI added to `AndroidManifest.xml`
3. ✅ Navigation integration added to `MainActivity.kt`
4. ✅ Notification permission added

---

## 🚀 How to Test

### **First Run Experience:**

1. **Build and install the app**
   ```bash
   ./gradlew installDebug
   ```

2. **Launch the app** - You should see:
   - Permission request screen (for mic/camera)
   - Then **ModelDownloadScreen** (since model isn't downloaded)

3. **Download Flow:**
   - Tap "Download AI Model"
   - Browser opens for HuggingFace OAuth
   - Sign in with your HuggingFace account
   - Accept the Gemma model agreement
   - Browser redirects back to app
   - Download starts automatically
   - Progress bar shows percentage + speed + ETA

4. **During Download:**
   - See notification in status bar
   - Progress updates every 200ms
   - Can cancel with X button
   - If app is closed, download continues in background

5. **After Download:**
   - Automatically navigates to main app screen
   - Model is ready to use
   - Next app launch skips download screen

---

## 🧪 Testing Checklist

### **Test 1: OAuth Flow**
- [ ] Tap "Download AI Model"
- [ ] Browser opens HuggingFace login
- [ ] Sign in successfully
- [ ] Accept Gemma license
- [ ] Redirects back to app
- [ ] Download starts

### **Test 2: Download Progress**
- [ ] Progress bar shows 0% → 100%
- [ ] Speed (MB/s) is displayed
- [ ] ETA updates correctly
- [ ] Notification shows in status bar

### **Test 3: Download Resume**
- [ ] Start download
- [ ] Force-close app mid-download
- [ ] Reopen app
- [ ] Tap "Resume Download"
- [ ] Download continues from where it stopped

### **Test 4: Cancel Download**
- [ ] Start download
- [ ] Tap X button
- [ ] Download stops
- [ ] Button shows "Download AI Model" again

### **Test 5: Subsequent Launches**
- [ ] After download completes
- [ ] Close app
- [ ] Reopen app
- [ ] Main screen shows directly (no download screen)

---

## 🐛 Common Issues & Solutions

### **Issue 1: OAuth Browser Doesn't Open**
**Solution:**
- Check logcat for errors: `adb logcat | grep HuggingFace`
- Verify client ID is correct in `HuggingFaceAuthHelper.kt`
- Verify redirect URI in manifest matches HuggingFace app settings

### **Issue 2: Download Doesn't Start**
**Solution:**
- Check internet connection
- Verify HuggingFace URL is accessible
- Check logcat: `adb logcat | grep GemmaDownloadWorker`
- Verify storage permission granted

### **Issue 3: "Forbidden" Error**
**Solution:**
- You haven't accepted the Gemma license on HuggingFace
- Go to: https://huggingface.co/google/gemma-3n-E2B-it-litert-lm
- Click "Agree and access repository"
- Try download again

### **Issue 4: Download Progress Stuck**
**Solution:**
- Check network speed (model is ~3 GB)
- Wait a few minutes (initial connection can be slow)
- Check logcat for HTTP errors
- Cancel and restart download

### **Issue 5: Notification Not Showing**
**Solution:**
- Grant POST_NOTIFICATIONS permission (Android 13+)
- Check notification settings in Android Settings

---

## 📊 Monitor Download Progress

### **Via Logcat:**
```bash
# Watch download worker logs
adb logcat | grep GemmaDownloadWorker

# Watch repository logs
adb logcat | grep GemmaDownloadRepository

# Watch ViewModel logs
adb logcat | grep GemmaDownloadViewModel
```

### **Check Download Status:**
```bash
# List running WorkManager workers
adb shell dumpsys activity service androidx.work.impl.background.systemalarm.SystemAlarmService
```

### **Check Model File:**
```bash
# Check if model file exists
adb shell ls -lh /sdcard/Android/data/com.whereikept.app/files/models/gemma_3n_e2b_it_int4/1.0/

# Check partial download (.tmp file)
adb shell ls -lh /sdcard/Android/data/com.whereikept.app/files/models/gemma_3n_e2b_it_int4/1.0/*.tmp
```

---

## 🎯 Expected Download Times

Based on network speed:

| Speed | Download Time |
|-------|---------------|
| 10 Mbps | ~40 minutes |
| 25 Mbps | ~16 minutes |
| 50 Mbps | ~8 minutes |
| 100 Mbps | ~4 minutes |
| WiFi 6 | ~2 minutes |

**Note:** First-time downloads may be slower due to HuggingFace CDN routing.

---

## 🔧 Debug Mode (Skip OAuth for Testing)

To test download without OAuth:

1. Temporarily use a public model URL in `GemmaModel.kt`:
   ```kotlin
   val url: String = "https://example.com/test-model.bin" // Small test file
   ```

2. This bypasses HuggingFace OAuth
3. Good for testing download/resume logic
4. Switch back to real URL after testing

---

## ✨ Success Indicators

Your implementation is working if you see:

1. ✅ **ModelDownloadScreen shows on first launch**
2. ✅ **OAuth browser opens when tapping download**
3. ✅ **Progress bar animates smoothly 0-100%**
4. ✅ **Download speed and ETA update in real-time**
5. ✅ **Notification shows in status bar**
6. ✅ **Download resumes after app restart**
7. ✅ **Main screen shows after download completes**
8. ✅ **Subsequent launches skip download screen**

---

## 📱 Test on Real Device

**Important:** Test on a real Android device, not emulator:

- Emulators have slow storage I/O
- Network speeds are unpredictable
- Background services may not work properly
- OAuth browser flow works better on real device

---

## 🚨 Before Release

Final checks:

- [ ] Test on Android 8.0+ (API 26+)
- [ ] Test on Android 13+ (notification permission)
- [ ] Test with slow network (3G/4G)
- [ ] Test with interruptions (airplane mode, low battery)
- [ ] Test storage full scenario
- [ ] Verify model file is ~3 GB after download
- [ ] Test app restart during download
- [ ] Verify no crashes in logcat

---

## 🎉 You're Ready!

If all tests pass, your Gemma model download feature is production-ready!

**Next Steps:**
1. Test thoroughly with real HuggingFace account
2. Verify OAuth flow works smoothly
3. Test download on various network conditions
4. Ship it! 🚀

---

## 📞 Need Help?

Check these logs for debugging:
- `GemmaDownloadWorker` - Download progress
- `GemmaDownloadRepository` - Repository operations
- `GemmaDownloadViewModel` - UI state changes
- `HuggingFaceAuthHelper` - OAuth flow
- `LlmService` - Model initialization

Happy testing! 🎊
