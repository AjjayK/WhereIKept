# WhereIKept Enhanced Workflow - Implementation Summary

## Overview
Successfully implemented the enhanced capture workflow for WhereIKept app with the following features:
- **Audio Recording** → **Image Capture** → **AI Analysis** → **Tag Review & Editing** → **Save to Database**

## Files Created/Modified

### 1. New Data Models
**File:** `app/src/main/java/com/whereikept/app/viewmodel/CaptureModels.kt`
- `CaptureWorkflowState` enum (9 states: IDLE, RECORDING, IMAGE_CAPTURE, TRANSCRIBING, ANALYZING, REVIEW_EDITING, SUBMITTING, SUCCESS, ERROR)
- `CaptureUiState` data class with all workflow state
- `ImageTag` and `EditableTag` models for tag management
- `ErrorState` with error types and retry logic

### 2. Enhanced LLMService
**File:** `app/src/main/java/com/whereikept/app/utils/LlmService.kt`

**Added Methods:**
- `extractItemsWithImage(transcript: String, imageUri: Uri): ExtractionResponse`
  - Multimodal analysis combining transcript + captured image
  - Uses Gemma 3N vision capabilities via LiteRT-LM `Message.of(prompt, bitmap)`
  - Fallback to text-only extraction if image processing fails

- `generateTagsForDatabase(transcript: String, tags: List<String>): ExtractionResponse`
  - Converts user-edited tags back to structured format for DB insertion

- `loadBitmapFromUri(uri: Uri): Bitmap?`
  - Helper to load images from URI

- `buildMultimodalExtractionPrompt(transcript: String): String`
  - Specialized prompt for image + text analysis

### 3. CaptureViewModel
**File:** `app/src/main/java/com/whereikept/app/viewmodel/CaptureViewModel.kt`

**Key Features:**
- State machine managing workflow transitions
- Integration with SpeechRecognitionHelper for audio recording
- Integration with LlmService for multimodal analysis
- Tag management (add, edit, delete, position)
- Database persistence via Repository
- Error handling with retry/skip logic

**State Transitions:**
```
IDLE → RECORDING → IMAGE_CAPTURE → TRANSCRIBING/ANALYZING → REVIEW_EDITING → SUBMITTING → SUCCESS
                                                                                ↓
                                                                              ERROR
```

**Key Methods:**
- `startRecording()`, `stopRecording()`, `cancelRecording()`
- `onImageCaptured(uri: Uri)`, `skipImage()`
- `analyzeWithGemma()` - triggers multimodal analysis
- Tag management: `updateTagPosition()`, `addNewTag()`, `deleteTag()`, `updateTagText()`
- `saveAndSubmit()` - saves to database
- `retryFromError()`, `skipError()`, `resetToIdle()`

### 4. UI Components
**File 1:** `app/src/main/java/com/whereikept/app/ui/CaptureScreen.kt`
**File 2:** `app/src/main/java/com/whereikept/app/ui/CaptureScreenPart2.kt`

**Components Created:**

#### Main Entry Point:
- `CaptureScreen()` - Main composable with state-based routing
- Camera launcher integration with FileProvider

#### State Screens:
1. **IdleStateScreen** - Welcome screen with "Start Recording" button
2. **RecordingStateScreen** - Shows recording timer, audio level, animated visualization
3. **ImageCaptureStateScreen** - Camera capture prompt with skip option
4. **TranscribingStateScreen** - Loading indicator for Whisper transcription
5. **AnalyzingStateScreen** - Shows image + transcript while Gemma analyzes
6. **ReviewEditingStateScreen** - Main editing UI with:
   - Tag overlay canvas on image
   - Draggable tags
   - Unplaced tags list
   - All tags list with edit/delete
   - Collapsible transcript
   - Save & Submit button
7. **SubmittingStateScreen** - Saving progress indicator
8. **SuccessStateScreen** - Success message with items saved summary
9. **ErrorStateScreen** - Error display with retry/skip/cancel options

#### Supporting Components:
- `AnimatedRecordingIndicator` - Animated ripples during recording
- `TagOverlayCanvas` - Image with draggable tag overlays
- `DraggableTagChip` - Individual draggable tag with edit button
- `TagChip` - Simple tag display
- `TagListItem` - Tag in list view with edit/delete buttons
- `AddTagDialog` - Dialog to add new tag
- `EditTagDialog` - Dialog to edit existing tag

### 5. Integration Updates
**File:** `app/src/main/java/com/whereikept/app/ui/Screens.kt`

**Changes:**
- Modified `MainScreen` to instantiate `CaptureViewModel` for tab 0
- Changed "Record" tab to "Capture" with camera icon
- Integrated `CaptureScreen()` composable

### 6. Configuration
**File:** `app/src/main/res/xml/file_paths.xml` ✅ Already exists
**File:** `app/src/main/AndroidManifest.xml` ✅ FileProvider already configured

## Workflow Details

### Step-by-Step User Flow

1. **User taps "Start Recording"**
   - State: IDLE → RECORDING
   - SpeechRecognitionHelper starts capturing audio
   - UI shows animated recording indicator + timer + audio level

2. **User taps "Stop Recording"**
   - State: RECORDING → IMAGE_CAPTURE
   - Audio saved to WAV file
   - Whisper transcription starts in background

3. **User captures image (or skips)**
   - Camera intent launched via FileProvider
   - State: IMAGE_CAPTURE → TRANSCRIBING (if transcript not ready) or ANALYZING
   - Image URI stored in state

4. **Transcription completes**
   - If image already captured → move to ANALYZING
   - Otherwise stay in IMAGE_CAPTURE state

5. **Gemma Analysis**
   - State: ANALYZING
   - Call `LlmService.extractItemsWithImage(transcript, imageUri)` if image present
   - Otherwise call `extractItemsFromTranscription(transcript)`
   - Extract object-location pairs as ImageTags

6. **User reviews & edits tags**
   - State: REVIEW_EDITING
   - Tags shown in two places:
     - **On image**: Draggable chips (if positioned)
     - **Unplaced list**: Tags not yet positioned
   - User can:
     - Drag tags onto image to position them
     - Tap tag to edit text
     - Add new tags manually
     - Delete wrong tags
   - All tags listed below with edit/delete buttons

7. **User taps "Save & Submit"**
   - State: REVIEW_EDITING → SUBMITTING
   - Convert tags to ItemEntity objects
   - Save to database via Repository
   - Save RecordingEntity with transcript
   - Save ImageEntity if image captured

8. **Success**
   - State: SUBMITTING → SUCCESS
   - Show summary of saved items
   - Auto-reset to IDLE after 3 seconds

### Error Handling

Each error type has specific recovery options:

| Error Type | Retryable? | Skippable? | Action |
|------------|------------|------------|---------|
| PERMISSION_DENIED | No | No | Show settings prompt |
| WHISPER_FAILED | Yes | No | Retry transcription |
| GEMMA_FAILED | Yes | Yes | Retry analysis OR skip to manual tag entry |
| CAMERA_FAILED | Yes | Yes | Retry camera OR skip image |
| DB_ERROR | Yes | No | Retry save |

## Technical Architecture

### State Management
```kotlin
// Single source of truth
CaptureUiState {
    workflowState: CaptureWorkflowState
    isRecording: Boolean
    recordingDuration: Long
    audioLevel: Float
    capturedImageUri: Uri?
    transcriptionText: String
    editableTags: List<EditableTag>
    error: ErrorState?
    // ... more fields
}
```

### Data Flow
```
User Input
    ↓
ViewModel (State Machine)
    ↓
Services (SpeechRecognitionHelper, LlmService)
    ↓
Repository (Database)
    ↓
UI State Updates (via StateFlow)
    ↓
Composables (Recompose)
```

### Concurrency
- **Audio Recording**: `SpeechRecognitionHelper` runs on background coroutine
- **Whisper Transcription**: Runs on `Dispatchers.IO` in background while user captures image
- **Gemma Analysis**: Runs on `Dispatchers.IO`, UI shows loading state
- **Database Operations**: Suspended functions on `Dispatchers.IO`

### Camera Integration
- Uses `ActivityResultContracts.TakePicture()`
- FileProvider configured for secure URI access
- Temporary files in cache directory
- URIs passed to ViewModel on capture success

## Features Implemented

✅ **State Machine Workflow** - 9 distinct states with proper transitions
✅ **Audio Recording** - Continuous recording with visual feedback
✅ **Background Transcription** - Whisper runs while user captures image
✅ **Multimodal Analysis** - Gemma 3N analyzes both image + transcript
✅ **Tag Overlay System** - Draggable tags on captured image
✅ **Tag Editing** - Add, edit, delete, position tags
✅ **Error Recovery** - Retry/skip options for each error type
✅ **Database Persistence** - Items, recordings, images saved
✅ **Success Feedback** - Clear confirmation with summary

## What's NOT Implemented (Future Enhancements)

🔲 **Screenshot Capture** - Composite image + tags for final DB submission (currently saves image URI)
🔲 **Undo/Redo** - Tag editing history
🔲 **Multi-select Delete** - Bulk tag removal
🔲 **Tag Confidence Filtering** - Hide low-confidence tags
🔲 **Offline Sync** - Handle network failures
🔲 **Tag Suggestions** - Auto-suggest based on past items
🔲 **Voice Feedback** - TTS confirmation of saved items

## Dependencies Used

All dependencies already in `build.gradle.kts`:
- **Compose** - UI framework
- **Room** - Database (with FTS for search)
- **ViewModel & Lifecycle** - State management
- **Coil** - Image loading (`AsyncImage`)
- **LiteRT-LM** - On-device Gemma 3N inference
- **Gson** - JSON parsing
- **Coroutines** - Async operations
- **CameraX** - Camera integration
- **FileProvider** - Secure file sharing

## Testing Checklist

### Manual Testing Steps:

1. **Happy Path:**
   - [ ] Start recording → speak clearly → stop recording
   - [ ] Capture image of location
   - [ ] Verify transcription shown
   - [ ] Verify tags extracted (object → location)
   - [ ] Drag tags onto image
   - [ ] Edit tag text
   - [ ] Add manual tag
   - [ ] Save → verify success message
   - [ ] Check Items tab → verify items saved

2. **Skip Image Flow:**
   - [ ] Start recording → speak → stop
   - [ ] Tap "Skip Image"
   - [ ] Verify tags extracted from text only
   - [ ] Save successfully

3. **Error Handling:**
   - [ ] Deny microphone permission → verify error
   - [ ] Silent recording → verify "no speech detected"
   - [ ] Bad network → verify Gemma error with retry
   - [ ] Retry from error state

4. **Edge Cases:**
   - [ ] Record very short audio (< 1 second)
   - [ ] Record very long audio (> 60 seconds)
   - [ ] No tags extracted → verify empty state UI
   - [ ] Cancel recording mid-way → verify reset
   - [ ] Switch tabs during workflow → verify state preserved

## Known Limitations

1. **Image Analysis**: Gemma 3N vision support via LiteRT-LM requires bitmap input - not all Gemma models support vision
2. **Tag Positioning**: Coordinates are relative pixels, not % (may not scale across devices)
3. **Transcription Speed**: Whisper tiny model is fast but may have lower accuracy
4. **Memory**: Large images may cause OOM on low-end devices

## Architecture Decisions

### Why Single ViewModel Instead of Multiple?
- Simpler state management with single source of truth
- Easier to handle state transitions between workflow steps
- Avoids issues with state sharing between ViewModels

### Why Separate UI Files (CaptureScreen + CaptureScreenPart2)?
- Single file was too large (>1000 lines)
- Logical separation: main states in CaptureScreen, editing components in Part2
- Easier to maintain and review

### Why StateFlow Instead of LiveData?
- Better Compose integration
- Cleaner syntax with `collectAsState()`
- More Kotlin idiomatic

### Why Tags as "object → location" Text?
- Simple user-editable format
- Easy to parse for database insertion
- Matches natural language patterns

## Next Steps for Production

1. **Add Logging/Analytics**
   - Track workflow completion rates
   - Identify failure points
   - Monitor performance metrics

2. **Add Crash Reporting**
   - Integrate Firebase Crashlytics
   - Report LLM failures

3. **Optimize Performance**
   - Lazy load images
   - Cache bitmaps
   - Reduce recompositions

4. **Improve UX**
   - Add haptic feedback
   - Smooth animations
   - Loading skeleton screens

5. **Accessibility**
   - TalkBack support
   - Content descriptions
   - High contrast mode

6. **Testing**
   - Unit tests for ViewModel
   - UI tests for critical flows
   - Integration tests for LLM services

## Conclusion

The enhanced workflow is **fully implemented** and ready for testing. The code follows Android best practices with:
- Clean architecture (ViewModel, Repository, UI separation)
- Reactive state management (StateFlow)
- Error handling with user-friendly recovery
- Modular composables for maintainability

The implementation provides a solid foundation for the WhereIKept app's core functionality while keeping the existing LLMService and LibWhisper integration intact.
