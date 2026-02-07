# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug      # Build debug APK
./gradlew assembleRelease    # Build release APK
./gradlew test               # Run unit tests
./gradlew connectedAndroidTest  # Run instrumented tests on connected device
```

## Project Overview

MedTimer is a native Android meditation timer app built with Kotlin and Jetpack Compose. It features configurable countdown, interval bells, white noise, and session history with CSV export.

**Tech Stack:** Kotlin 1.9.22, Jetpack Compose, Room Database, Material Design 3, Android SDK 34 (min 26)

## Architecture

**MVVM with Foreground Service:**

- `MeditationViewModel` - Manages UI state via `MeditationUiState` data class with StateFlow. Central hub for user actions and service callbacks.
- `MeditationTimerService` - Foreground service that survives app backgrounding. Uses `SystemClock.elapsedRealtime()` for accurate timing that survives phone sleep. Communicates with ViewModel via callback interface.
- `BellPlayer` - Audio manager handling interval/final bells and gapless white noise looping (dual MediaPlayer technique).

**Timer State Machine:**
```
IDLE → COUNTDOWN → MEDITATING → FINISHED
         ↓            ↓
      (stop)       (stop) → Dialog → save/discard
```

**Key Parameters:**
- C: countdown seconds before meditation starts
- N: minutes between interval bells
- K: number of intervals (total time = N × K minutes)

**Database:** Room with single `Session` entity (date, startTime, elapsedSeconds). SessionDao provides Flow-based reactive queries.

## Key Files

- `app/src/main/java/com/medtimer/app/MeditationViewModel.kt` - Core state management
- `app/src/main/java/com/medtimer/app/service/MeditationTimerService.kt` - Timer logic in foreground service
- `app/src/main/java/com/medtimer/app/audio/BellPlayer.kt` - Audio playback
- `app/src/main/java/com/medtimer/app/ui/HomeScreen.kt` - Main timer UI
- `app/src/main/java/com/medtimer/app/data/` - Room database setup

## Debug Mode

A debug flag exists in the UI that counts in seconds instead of minutes for faster testing.

## Design Guidelines

- Earth-tone color palette (sage green, warm brown, taupe, cream)
- Modern but uncluttered appearance
- Restful colors, not bright ones
