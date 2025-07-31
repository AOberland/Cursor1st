#!/bin/bash

echo "🧹 Cleaning Android project to resolve build issues..."

# Navigate to project directory
echo "📁 Project directory: $(pwd)"

# Clean Gradle cache and build directories
echo "🗑️  Cleaning Gradle caches..."
./gradlew clean --no-daemon

# Clear Gradle cache directory
echo "🗑️  Clearing Gradle cache directory..."
rm -rf ~/.gradle/caches/
rm -rf ~/.gradle/wrapper/

# Clear local build directories
echo "🗑️  Removing build directories..."
find . -name "build" -type d -exec rm -rf {} + 2>/dev/null || true
find . -name ".gradle" -type d -exec rm -rf {} + 2>/dev/null || true

# Clear Android Studio cache (if exists)
echo "🗑️  Clearing Android Studio cache..."
rm -rf ~/.cache/Google/AndroidStudio*
rm -rf ~/.android/build-cache/

# Clear any lock files
echo "🗑️  Removing lock files..."
find . -name "*.lock" -delete 2>/dev/null || true

echo "✅ Clean completed!"
echo ""
echo "🔧 Now run the build:"
echo "   ./gradlew assembleDebug --no-daemon --refresh-dependencies"
echo ""
echo "🏗️  Or in Android Studio:"
echo "   1. File → Invalidate Caches and Restart"
echo "   2. Build → Clean Project"
echo "   3. Build → Rebuild Project"