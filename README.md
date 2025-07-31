# Undercarriage Scanner Android App

An intelligent Android camera application for scanning undercarriages of vehicles and machinery using computer vision, AI guidance, and sonic feedback.

## Features

### Core Functionality
- **Smart Camera System**: CameraX-based camera with low-light optimizations
- **AI-Powered Guidance**: Real-time movement detection and coverage mapping
- **Sonic Feedback**: Directional audio guidance and haptic feedback
- **Auto-Capture**: Intelligent image capture when device is stable
- **Coverage Tracking**: Real-time visualization of scan progress

### Technical Highlights
- **Offline Operation**: All processing happens on-device
- **Computer Vision**: OpenCV-based feature detection and image stitching
- **Machine Learning**: TensorFlow Lite for coverage analysis
- **Low-Light Performance**: Optimized for garage and undercarriage environments

## Requirements

### Hardware
- Android 9.0+ (API 28+)
- Back-facing camera with autofocus
- Gyroscope and compass sensors
- At least 3GB RAM (recommended)

### Software Dependencies
- CameraX 1.3.1
- OpenCV 4.8.0
- TensorFlow Lite 2.14.0
- Material Design Components 1.11.0

## Installation

1. Clone the repository:
```bash
git clone <repository-url>
cd undercarriage-scanner
```

2. Open in Android Studio:
   - File → Open → Select the project directory
   - Allow Gradle sync to complete

3. Build and install:
   - Connect Android device or start emulator
   - Run → Run 'app'

## Architecture

```
app/
├── camera/          # CameraX implementation
├── ai/              # Computer vision and ML components
├── audio/           # Sonic feedback system
├── stitching/       # Image stitching pipeline (Phase 2)
└── utils/           # Utility classes
```

## Usage

### Basic Operation
1. **Start the app** and grant camera permissions
2. **Position your device** under the vehicle/machinery
3. **Tap "Start Scanning"** to begin coverage tracking
4. **Follow audio guidance** to move the camera systematically
5. **Auto-capture** occurs when the device is stable
6. **Monitor progress** via the coverage percentage indicator
7. **Complete** when 85%+ coverage is achieved

### Controls
- **Start/Stop**: Begin or end scanning session
- **Manual Capture**: Take photo manually (disabled during auto-scan)
- **Flashlight**: Toggle device flashlight
- **Reset**: Clear coverage data and start over
- **View Results**: See captured images and coverage heatmap

### Audio Guidance
- **Directional beeps**: Left/right speaker indicates movement direction
- **Pitch variation**: Higher pitch = closer to optimal position
- **Vibration patterns**: Different patterns for various guidance states

## Development Phases

### Phase 1: MVP ✅
- [x] Basic camera with low-light optimization
- [x] Movement guidance system
- [x] Coverage tracking algorithm
- [x] Auto-capture functionality
- [x] Sonic feedback implementation

### Phase 2: Enhanced (In Progress)
- [ ] Image stitching pipeline
- [ ] Defect detection (rust/damage highlighting)
- [ ] Advanced AR overlay for gaps
- [ ] Export functionality (PDF reports)

### Phase 3: Advanced (Future)
- [ ] 3D reconstruction
- [ ] Cloud backup and analysis
- [ ] Multi-device synchronization
- [ ] Professional reporting tools

## Configuration

Key constants can be modified in:
- `CoverageAnalyzer.kt`: Coverage thresholds, stability settings
- `SonicFeedbackManager.kt`: Audio timing and feedback patterns
- `CameraManager.kt`: Camera resolution and exposure settings

## Testing

### Device Matrix
Test on devices representing different performance tiers:
- **Low-end**: 2GB RAM, older processors
- **Mid-range**: 4GB RAM, modern SoC
- **High-end**: 8GB+ RAM, flagship performance

### Test Scenarios
- **Lighting conditions**: 0-1000 lux environments
- **Surface types**: Various undercarriage materials and textures
- **Movement patterns**: Different scanning approaches

## Troubleshooting

### Common Issues

**Camera fails to initialize**
- Check camera permissions
- Verify device has back-facing camera
- Restart app if CameraX binding fails

**OpenCV not loading**
- Ensure OpenCV Manager is installed (for some devices)
- Check device architecture compatibility

**Poor low-light performance**
- Enable flashlight
- Move closer to surface
- Clean camera lens

**Audio feedback not working**
- Check device volume settings
- Verify vibration permissions
- Test with headphones if needed

## Performance Optimization

- **Memory management**: Properly release camera resources
- **Battery optimization**: Pause processing when app is backgrounded
- **Storage**: Automatic cleanup of old capture sessions
- **CPU usage**: Adjustable analysis frame rate based on device performance

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Privacy & Compliance

- **Data processing**: All image analysis happens on-device
- **No cloud uploads**: Images remain on local device storage
- **Permissions**: Only camera and vibration permissions required
- **GDPR compliant**: No personal data collection or transmission

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:
- Check the troubleshooting section above
- Review existing GitHub issues
- Create a new issue with detailed description and device information

## Roadmap

See our [GitHub Projects](link-to-projects) for detailed development roadmap and upcoming features.

---

**Note**: This app is designed for professional use in automotive, machinery inspection, and industrial applications. Always follow safety protocols when working around vehicles and machinery.
