# Clepsy Android

<p align="center">
	<img src="clepsy_black_white_logo.png" alt="Clepsy logo" width="128" />
</p>

> ⚠️ **Alpha Software** - Clepsy is currently in alpha. Expect breaking changes and rough edges.

## Overview

Clepsy Android is a native monitoring client for Android devices. It runs as a foreground service, tracks app usage events enriched with notification and media metadata, detects when the device is idle, and securely transmits activity data to the Clepsy backend.

## Features

- Foreground service with persistent notification for transparent monitoring
- Automatic tracking of foreground app usage with activity names and labels
- Enrichment with notification text and media metadata (title, artist, duration etc.)
- Intelligent idle detection based on screen state and device interactivity
- Secure, encrypted data transmission to the Clepsy backend
- Lightweight background operation with minimal battery impact
- Material 3 UI with dark mode support

## Installation

Prebuilt APKs are automatically generated for each release via GitHub Actions. Download the appropriate APK for your device from the [Releases page](https://github.com/SamGalanakis/clepsy-android-source/releases).

### Installing the APK

1. Download `app-release.apk` (signed) or `app-debug.apk` from the latest release.
2. Enable **Install from Unknown Sources** in your device settings if prompted.
3. Open the downloaded APK and follow the installation prompts.
4. Launch **Clepsy** from your app drawer.
5. Grant the required permissions when prompted:
   - **Usage Access** – Required to detect foreground app changes
   - **Notification Listener Access** – Required to capture notification text and media metadata
   - **Notification Permission** (Android 13+) – Required for the foreground service notification

### Android requirements and notes

- **Permissions:** The app requires Usage Stats access and Notification Listener access. These are sensitive permissions that must be granted through system settings.
- **Foreground service:** A persistent notification will appear while monitoring is active. This is required by Android for long-running background services.
- **Battery optimization:** For best results, exclude Clepsy from battery optimization in your device settings to prevent the service from being killed.
- **Screen off behavior:** The service automatically pauses monitoring when the screen is off and resumes when the device wakes.

## Development

### Prerequisites

- **Android Studio** Flamingo or newer
- **Java 17** – The project and CI build with Temurin 17
- **Android SDK 35** – Install via SDK Manager

### Building locally

```bash
# Clone the repository
git clone https://github.com/SamGalanakis/clepsy-android-source.git
cd clepsy-android-source

# Build debug APK
./gradlew assembleDebug

# Install to connected device/emulator
./gradlew installDebug

# Run unit tests
./gradlew testDebugUnitTest
```

### Running the app

1. Open the project in Android Studio
2. Connect an Android device or start an emulator
3. Click **Run** or press `Shift+F10`
4. Grant required permissions in the app's onboarding flow


## License

This project is dual-licensed:

- **Open Source License:** GNU Affero General Public License v3.0 (AGPL-3.0) for open source use. See the [LICENSE](LICENSE) file for details.
- **Commercial License:** For commercial licensing options please contact [sam@clepsy.ai](mailto:sam@clepsy.ai).

**Copyright © 2025 Samouil Galanakis. All rights reserved.**
