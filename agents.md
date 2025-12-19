# Developer Guidelines - WhereIKept

## Project Overview
**WhereIKept** is a personal memory assistant Android app using on-device AI (Whisper + Gemma) to extract and store object-location pairs from voice recordings.

## Technology Stack
- **Platform**: Android
- **Language**: Kotlin
- **Build System**: Gradle with Kotlin DSL
- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 34
- **Compile SDK**: 34
- **Kotlin Version**: 1.9.20
- **Java Target**: 17

## Architecture Pattern
**MVVM (Model-View-ViewModel)** with:
- **UI Layer**: Jetpack Compose with Material 3
- **ViewModel Layer**: StateFlow-based state management
- **Data Layer**: Room database with Repository pattern
- **Services**: Speech recognition, LLM integration (Ollama/OpenAI)

## Project Structure
```
WhereIKept/
├── app/
│   ├── src/main/
│   │   ├── java/com/whereikept/app/
│   │   │   ├── data/          # Room entities, DAOs, database, repository
│   │   │   ├── ui/            # Compose screens and theme
│   │   │   ├── viewmodel/     # MainViewModel
│   │   │   ├── utils/         # LlmService, SpeechRecognitionHelper
│   │   │   ├── MainActivity.kt
│   │   │   └── WhereIKeptApplication.kt
│   │   ├── res/               # Resources (strings, colors, themes, drawables)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts       # App module build config
├── build.gradle.kts           # Root build config
├── settings.gradle.kts
├── .gitignore
└── README.md
```

## Key Dependencies
- **Jetpack Compose**: UI framework (Material 3)
- **Room 2.6.1**: Database with FTS4 full-text search
- **KSP**: Annotation processing for Room
- **Navigation Compose**: Screen navigation
- **CameraX**: Camera integration
- **Coil**: Image loading and caching
- **OkHttp 4.12.0**: HTTP client for LLM API calls
- **Gson**: JSON parsing
- **Coroutines**: Async operations
- **DataStore**: Preferences storage
- **ViewBinding**: Enabled (legacy support)

## Core Features
1. **Voice Recording**: Record and transcribe speech using Android SpeechRecognizer
2. **LLM Integration**: Extract object-location pairs via Ollama/OpenAI-compatible APIs
3. **Smart Search**: Full-text search with LLM-generated natural language responses
4. **Manual Entry**: Add items without voice recording
5. **Local Storage**: Room database with FTS4 for fast queries
6. **Camera Support**: Image capture for storage locations

## Development Guidelines

### Code Standards
- **Language**: Kotlin only (no Java)
- **Style**: Follow Kotlin official style guide
- Use meaningful, descriptive names for classes, functions, and variables
- Prefer `val` over `var` when possible
- Use null safety features (`?.`, `!!`, `?:`)
- Add KDoc comments for public APIs and complex logic
- Keep functions focused (single responsibility principle)

### Compose UI Best Practices
- Use Material 3 components from `androidx.compose.material3`
- Follow state hoisting principles
- Use `remember` and `rememberSaveable` appropriately
- Prefer stateless composables when possible
- Use `LaunchedEffect` for side effects
- Handle configuration changes properly

### ViewModel & State Management
- Use `StateFlow` for UI state
- Emit state updates via `_state.value = newState`
- Keep business logic in ViewModel, not in Composables
- Use `viewModelScope` for coroutines
- Handle errors and loading states explicitly

### Room Database
- Use FTS4 for full-text search capabilities
- Define entities with proper annotations
- Use DAOs with suspend functions
- Repository pattern for data access
- Use Flow for reactive queries

### Asynchronous Operations
- Use Kotlin Coroutines (not RxJava or callbacks)
- `viewModelScope` in ViewModels
- `lifecycleScope` in Activities (rare)
- Use `Dispatchers.IO` for database/network operations
- Use `Dispatchers.Main` for UI updates (default)

### LLM Integration
- Support both Ollama (local) and OpenAI-compatible APIs
- Default: Ollama at `http://localhost:11434` with model `llama3.2`
- Provide fallback pattern matching when LLM unavailable
- Use OkHttp for API calls
- Parse responses with Gson

### Permissions & Security
- Runtime permissions for: RECORD_AUDIO, CAMERA
- INTERNET permission for LLM communication
- Never commit API keys or secrets
- Use DataStore for storing user preferences (API URLs, models)
- All data stored locally (privacy-first)

### Resources & UI
- All user-facing strings in [strings.xml](app/src/main/res/values/strings.xml)
- Support dark mode (Material 3 handles this)
- Use vector drawables (XML) over PNGs
- Follow Material Design 3 guidelines
- Support multiple screen sizes and orientations

### Testing
- Unit tests for ViewModels and business logic (JUnit)
- Instrumentation tests for database and UI (Espresso, Compose UI Test)
- Mock external dependencies (LLM service, speech recognition)
- Test edge cases (offline mode, permission denials, LLM failures)

### Build Configuration
- Use Kotlin DSL for Gradle files
- ProGuard enabled for release builds
- ViewBinding enabled (legacy)
- Compose enabled with compiler version 1.5.5
- Vector drawable support library enabled

### Git Workflow
- Feature branches: `feature/feature-name`
- Bug fixes: `fix/bug-description`
- Clear, descriptive commit messages
- Never commit: `build/`, `.gradle/`, `local.properties`, API keys
- Use the provided `.gitignore`

## Documentation
- **[README.md](README.md)** - Project overview and quick start guide
- **[DEVELOPMENT.md](DEVELOPMENT.md)** - Implementation details and model setup
- **agents.md** (this file) - Developer guidelines and coding standards

## Required Permissions
```xml
RECORD_AUDIO    # Voice recording and transcription
CAMERA          # Image capture of storage locations
INTERNET        # LLM API communication (Ollama/OpenAI)
```

## Performance Considerations
- Room FTS4 for fast full-text search
- Use Coil for efficient image loading and caching
- Coroutines for non-blocking operations
- Avoid memory leaks (proper lifecycle handling)
- ProGuard/R8 for release builds (code shrinking)

## AI Agent Instructions

### When Adding Features
1. Follow existing MVVM architecture pattern
2. Add new screens in `ui/` package
3. Create ViewModels in `viewmodel/` package
4. Use Room for data persistence
5. Integrate with existing LLM service if needed
6. Update manifest for new permissions
7. Add strings to `strings.xml`

### When Fixing Bugs
1. Check ViewModel state management first
2. Verify coroutine scopes and dispatchers
3. Review Room queries and FTS4 usage
4. Test LLM fallback behavior
5. Ensure proper null safety
6. Test on different Android versions (SDK 26+)

### When Refactoring
1. Maintain MVVM separation of concerns
2. Keep Composables stateless when possible
3. Use Repository pattern for data access
4. Don't break LLM integration (Ollama/OpenAI)
5. Preserve FTS4 search functionality
6. Update tests accordingly

### Code Review Checklist
- [ ] Kotlin style guide followed
- [ ] State hoisting in Compose
- [ ] Coroutines used properly (correct dispatchers)
- [ ] Room queries optimized
- [ ] Strings externalized to `strings.xml`
- [ ] Permissions handled at runtime
- [ ] Error states handled
- [ ] Null safety respected
- [ ] No memory leaks (lifecycle-aware)
- [ ] Tests added/updated

## Project-Specific Patterns

### Speech Recognition
- Use `SpeechRecognitionHelper` utility
- Handle partial results for real-time feedback
- Gracefully handle speech recognition unavailability

### LLM Communication
- Use `LlmService` for all LLM interactions
- Support configurable API endpoints
- Always provide fallback behavior
- Parse JSON responses carefully (handle errors)

### Database Queries
- Use FTS4 MATCH queries for search
- Return Flow<List<T>> for reactive UI updates
- Use suspend functions in DAOs

### Build & Gradle
- **NEVER run `./gradlew build` from Cursor/IDE terminal**
- Use Android Studio's build system instead
- Gradle builds should be triggered via Android Studio UI
- Only run specific Gradle tasks if explicitly requested (e.g., `./gradlew clean`)

## Future Enhancements Roadmap
- Image analysis with vision LLM
- Voice queries
- Categories/room organization
- Reminders based on context
- Cloud backup and sync
- Home screen widgets
- Wear OS companion app

## Package Naming
- **Base Package**: `com.whereikept.app`
- **Data**: `com.whereikept.app.data`
- **UI**: `com.whereikept.app.ui`
- **ViewModel**: `com.whereikept.app.viewmodel`
- **Utils**: `com.whereikept.app.utils`

## Resources
- [Android Developer Docs](https://developer.android.com)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io)
- [Kotlin Style Guide](https://developer.android.com/kotlin/style-guide)
- [Room Documentation](https://developer.android.com/training/data-storage/room)
- [MediaPipe LLM](https://developers.google.com/mediapipe/solutions/genai/llm_inference)
- [Whisper.cpp](https://github.com/ggerganov/whisper.cpp)
