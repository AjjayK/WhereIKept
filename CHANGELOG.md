# Changelog

All notable changes to WhereIKept will be documented in this file.

## [1.1.2] - 2026-02-13

### Performance
- Release Whisper model from memory after transcription completes to free ~74 MB of native memory
- Lazy re-initialization of Whisper model when user starts a new recording

### Documentation
- Consolidated project docs into README, CHANGELOG, ARCHITECTURE, and TESTING_GUIDE
- Moved marketing files to docs/marketing/ (excluded from git)

## [1.1.1] - 2026-02-08

### Fixed
- Google Play Console "Incomplete advertising ID declaration" blocking closed testing release
- Added advertising ID declaration to AndroidManifest.xml (declared as not used)

### Added
- Example phrase on capture screen idle state to guide new users ("I'm putting my passport in the bedroom drawer")

## [1.1.0] - 2026-02-07

### Added
- Firebase Analytics integration
- Analytics repository for managing data collection and storage
- Device info collector for hardware and OS tracking
- Resource monitor for memory and battery usage
- Inference metrics collector for LLM performance tracking
- Error classifier for categorizing and tracking errors
- Version metrics collector for app version analytics
- Analytics statistics screen with data visualization
- Analytics consent dialog for user privacy
- Room database schema extended with analytics tables and DAOs

## [1.0.1] - 2025-12-31

### Added
- Settings screen with privacy policy link (Google Play Store requirement)
- Improved Gemma model download workflow at app initialization
- GPU backend integration for image processing and tag generation
- Enhanced LLM prompts and reprocessing capabilities after tagging
- App launcher icons and production signing key
- HuggingFace authentication helper for model downloads
- Background model download worker

### Changed
- Complete UI overhaul across all screens
- Updated database schema with fixes and enhancements

### Fixed
- Drag and drop issues in UI
- Schema-related fixes across multiple components
- HuggingFace 403 error hotfix

## [1.0.0] - 2025-12-30

### Added
- Full capture workflow: Audio Recording → Image Capture → AI Analysis → Tag Review & Editing → Save
- Multimodal AI analysis using Gemma 3N vision model (audio transcript + captured image)
- Interactive tag management with drag-and-drop positioning on images
- 9-state workflow state machine (Idle → Recording → Image Capture → Transcribing → Analyzing → Review/Edit → Submitting → Success/Error)
- Background Whisper transcription while user captures images
- Skip image option for text-only analysis
- Manual tag entry and inline editing
- Error recovery with retry/skip options per workflow stage
- Database schema v4 with FTS (Full-Text Search) support

## [0.1.0] - 2025-12-22

### Added
- Initial working version with CPU backend
- On-device Whisper speech-to-text transcription (tiny.en model)
- LiteRT-LM integration for on-device LLM inference
- Basic item storage and search functionality
