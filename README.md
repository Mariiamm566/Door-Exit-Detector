# Door Exit Detector with Obstacle Avoidance

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![ML Kit](https://img.shields.io/badge/Google_ML_Kit-4285F4?style=for-the-badge&logo=google&logoColor=white)

An Android application that helps visually impaired users navigate to doors while avoiding obstacles using real-time object detection and voice guidance.

## Features

- Real-time door detection using custom ML model
- Obstacle detection and avoidance guidance
- Voice feedback for navigation instructions
- Visual bounding boxes for detected objects
- Optimized performance with frame skipping
- Guidance cooldown to prevent speech spamming

## Prerequisites

- Android device with camera (API level 21+)
- Android Studio (latest version recommended)
- Google ML Kit dependencies
- Camera permissions

## Installation

1. Clone this repository:
   ```bash
   git clone [https://github.com/yourusername/scan.door.exitdetector.git](https://github.com/Mariiamm566/Door-Exit-Detector.git)
   ```

2. Open the project in Android Studio

3. Build and run on your Android device or emulator

## Usage

1. Grant camera permissions when prompted
2. Point your device's camera towards your environment
3. The app will:
   - Detect doors in the camera view
   - Identify obstacles in your path
   - Provide voice guidance for navigation
   - Show visual bounding boxes around detected objects

## Technical Details

- **Core Technologies**:
  - CameraX for camera operations
  - Google ML Kit with custom TensorFlow Lite model
  - TextToSpeech for voice guidance
  - Custom overlay view for object visualization

- **Model Information**:
  - Custom trained object detection model (`object_detection.tflite`)
  - Currently trained to detect doors and common obstacles
  - Minimum confidence threshold: 50% for obstacles, 70% for classification

## Customization

You can adjust several parameters in `MainActivity.kt`:

```kotlin
// Detection parameters
private const val MIN_OBSTACLE_CONFIDENCE = 0.5f
private const val guidanceCooldown = 20000L // 20 seconds
private const val frameSkip = 2 // Process every 3rd frame

// Visualization colors in setupOverlayStyle()
```

## Screenshots

(Add screenshots here showing the app in action with bounding boxes)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome! Please open an issue or submit a pull request for any improvements.

---
