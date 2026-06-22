# DelayCam

DelayCam is an Android app that shows a delayed live camera preview using frame buffering.

## Features

- Live camera preview
- Adjustable delay (0–10 seconds)
- Frame buffering for delayed playback

## Tech Stack

- Kotlin
- CameraX
- AndroidX

## Requirements

- Android Studio Hedgehog or newer
- Android device or emulator with camera support

## Build

### Minimum SDK
- minSdk: 21 (Android 5.0 Lollipop)

### Compile / Target SDK
- compileSdk: 34
- targetSdk: 34

### Steps to run

1. Clone the repository:

```bash
git clone https://github.com/chaimite/delaycam.git
```

2. Open the project in Android Studio:
Click Open
Select the project folder

3. Sync Gradle:
Wait for dependencies to download

4. Run the app:
Connect an Android device or start an emulator
Click Run ▶

## Permissions
- Grant camera permission when prompted

## Permissions
- Camera permission (required for preview)

## Notes
- Frames are stored in a buffer with timestamps
- Delay is achieved by selecting older frames from the buffer
- Setting delay to 0 enables near-live mode

## License

MIT License