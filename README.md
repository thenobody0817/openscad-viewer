# OpenSCAD Viewer for Android

A native Android application for viewing, editing, and exporting OpenSCAD (.scad) files with real-time 3D rendering using Google's Filament PBR engine.

## Features

- **Native OpenSCAD Parsing**: Uses OpenSCAD's C++ library via JNI for 100% compatibility
- **High-Quality Rendering**: Google Filament PBR engine with physically-based materials
- **Interactive Parameters**: Real-time variable sliders for parametric models
- **Code Editor**: Syntax-highlighted editor with live preview
- **Export**: STL, OBJ, and glTF export support
- **Modern UI**: Material 3 design with Jetpack Compose

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Android App (Kotlin)                   │
├─────────────────────────────────────────────────────────────┤
│  UI Layer (Compose)    │  ViewModels  │  Repository/DB       │
├─────────────────────────────────────────────────────────────┤
│                     JNI Bridge (C++)                         │
├─────────────────────────────────────────────────────────────┤
│  OpenSCAD C++ Lib    │  Filament    │  CGAL/OpenCSG/Boost   │
└─────────────────────────────────────────────────────────────┘
```

## Requirements

- Android Studio Hedgehog (2023.1.1) or later
- Android NDK r26b (26.1.10909125)
- JDK 17
- Gradle 8.5+

## Building

### 1. Build OpenSCAD Dependencies

First, build the native dependencies (CGAL, OpenCSG, GMP, MPFR, Eigen, Boost):

```bash
chmod +x build-openscad-deps.sh
./build-openscad-deps.sh
```

This creates `install_deps/<arch>/` with all libraries.

### 2. Build OpenSCAD Library for Android

```bash
# Clone OpenSCAD
git clone --recursive https://github.com/openscad/openscad.git
cd openscad

# Configure for Android using the built dependencies
mkdir build-android && cd build-android
cmake .. \
    -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-29 \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX=../../install_deps/arm64-v8a \
    -DBUILD_GUI=OFF \
    -DBUILD_TESTS=OFF \
    -DOPENSCAD_DEPS_PATH=../../install_deps/arm64-v8a

make -j$(nproc)
make install
```

Repeat for other architectures (armeabi-v7a, x86, x86_64).

### 3. Build Android App

```bash
./gradlew assembleDebug
```

## Project Structure

```
openscad-viewer/
├── app/                          # Main Android app module
│   ├── src/main/
│   │   ├── cpp/                  # JNI bridge C++ code
│   │   ├── java/com/openscad/viewer/
│   │   │   ├── data/             # Room database, DAOs
│   │   │   ├── di/               # Hilt dependency injection
│   │   │   ├── render/           # Filament rendering
│   │   │   ├── ui/               # Compose UI screens
│   │   │   └── util/             # Utilities
│   │   └── res/                  # Resources
│   └── build.gradle.kts
├── openscad-jni/                 # JNI module (shared)
│   ├── src/main/
│   │   ├── cpp/                  # OpenSCAD JNI wrapper
│   │   └── java/com/openscad/jni/ # Kotlin JNI interface
│   └── build.gradle.kts
├── build-openscad-deps.sh        # Dependency build script
├── gradle/                       # Gradle wrapper & version catalog
├── settings.gradle.kts
└── build.gradle.kts
```

## Key Components

### OpenSCADNative (openscad-jni)
Kotlin wrapper around the JNI interface providing:
- `parseAndEvaluate(code, variables)` - Full evaluation with mesh output
- `extractVariables(code)` - Fast variable extraction for UI
- `exportSTL/exportOBJ` - Export functionality

### FilamentRenderer (app/render)
High-level Filament wrapper handling:
- Engine/Scene/View/Camera setup
- Mesh upload and rendering
- Camera controls (orbit, pan, zoom)
- PBR material management

### ViewerViewModel
Manages viewer state:
- Current mesh and variables
- Loading/error states
- Variable value updates with re-evaluation

### EditorViewModel
Manages editor state:
- Code text
- Real-time variable analysis
- Synchronization with viewer

## Adding OpenSCAD Library

The JNI bridge expects `libopenscad.so` and its dependencies in `app/src/main/jnilibs/<arch>/`:

```
jnilibs/
├── arm64-v8a/
│   ├── libopenscad.so
│   ├── libcgal.so
│   ├── libopencsg.so
│   ├── libgmp.so
│   ├── libmpfr.so
│   ├── libmpfi.so
│   ├── libboost_system.so
│   ├── libboost_filesystem.so
│   ├── libboost_thread.so
│   └── libc++_shared.so
└── ... (other architectures)
```

## License

Apache 2.0 - See LICENSE file

## Acknowledgments

- [OpenSCAD](https://openscad.org/) - The OpenSCAD language and C++ library
- [Filament](https://github.com/google/filament) - Google's PBR rendering engine
- [CGAL](https://www.cgal.org/) - Computational Geometry Algorithms Library
- [OpenCSG](http://opencsg.org/) - Constructive Solid Geometry rendering