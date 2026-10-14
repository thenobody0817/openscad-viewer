#!/bin/bash
# Build script for OpenSCAD dependencies for Android
# This script builds CGAL, OpenCSG, MPFR, GMP, Eigen, etc. for Android NDK

set -e

# Configuration
ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-$HOME/Android/Sdk/ndk/26.1.10909125}
ANDROID_API=29
BUILD_DIR=$(pwd)/build_deps
INSTALL_DIR=$(pwd)/install_deps
NUM_JOBS=$(nproc)

# Architectures to build
ARCHS=("arm64-v8a" "armeabi-v7a" "x86" "x86_64")

echo "Building OpenSCAD dependencies for Android..."
echo "NDK: $ANDROID_NDK_HOME"
echo "API: $ANDROID_API"
echo "Build dir: $BUILD_DIR"
echo "Install dir: $INSTALL_DIR"

mkdir -p "$BUILD_DIR" "$INSTALL_DIR"

# Function to get toolchain file
get_toolchain() {
    echo "$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake"
}

# Function to build for one architecture
build_arch() {
    local arch=$1
    local api=$2
    local build_dir="$BUILD_DIR/$arch"
    local install_dir="$INSTALL_DIR/$arch"

    mkdir -p "$build_dir" "$install_dir"

    case $arch in
        arm64-v8a)
            ABI="arm64-v8a"
            TOOLCHAIN="aarch64-linux-android"
            ;;
        armeabi-v7a)
            ABI="armeabi-v7a"
            TOOLCHAIN="armv7a-linux-androideabi"
            ;;
        x86)
            ABI="x86"
            TOOLCHAIN="i686-linux-android"
            ;;
        x86_64)
            ABI="x86_64"
            TOOLCHAIN="x86_64-linux-android"
            ;;
    esac

    cmake -B "$build_dir" -S . \
        -DCMAKE_TOOLCHAIN_FILE=$(get_toolchain) \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="android-$api" \
        -DANDROID_STL=c++_shared \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX="$install_dir" \
        "$@"

    cmake --build "$build_dir" -j$NUM_JOBS
    cmake --install "$build_dir"
}

# Build dependencies in order
# 1. GMP
echo "Building GMP..."
cd "$BUILD_DIR"
wget -nc https://gmplib.org/download/gmp/gmp-6.3.0.tar.xz
tar -xf gmp-6.3.0.tar.xz
for arch in "${ARCHS[@]}"; do
    build_arch "$arch" "$ANDROID_API" ../gmp-6.3.0 \
        --disable-assembly \
        --enable-fat
done

# 2. MPFR
echo "Building MPFR..."
wget -nc https://www.mpfr.org/mpfr-current/mpfr-4.2.1.tar.xz
tar -xf mpfr-4.2.1.tar.xz
for arch in "${ARCHS[@]}"; do
    build_arch "$arch" "$ANDROID_API" ../mpfr-4.2.1 \
        --with-gmp="$INSTALL_DIR/$arch"
done

# 3. MPFI (required by CGAL)
echo "Building MPFI..."
wget -nc https://gforge.inria.fr/frs/download.php/latestfile/489/mpfi-1.5.4.tar.gz
tar -xf mpfi-1.5.4.tar.gz
for arch in "${ARCHS[@]}"; do
    build_arch "$arch" "$ANDROID_API" ../mpfi-1.5.4 \
        --with-gmp="$INSTALL_DIR/$arch" \
        --with-mpfr="$INSTALL_DIR/$arch"
done

# 4. Eigen (header-only)
echo "Installing Eigen..."
wget -nc https://gitlab.com/libeigen/eigen/-/archive/3.4.0/eigen-3.4.0.tar.gz
tar -xf eigen-3.4.0.tar.gz
for arch in "${ARCHS[@]}"; do
    mkdir -p "$INSTALL_DIR/$arch/include/eigen3"
    cp -r eigen-3.4.0/Eigen "$INSTALL_DIR/$arch/include/eigen3/"
    cp -r eigen-3.4.0/unsupported "$INSTALL_DIR/$arch/include/eigen3/"
done

# 5. CGAL
echo "Building CGAL..."
wget -nc https://github.com/CGAL/cgal/releases/download/v5.6/CGAL-5.6.tar.xz
tar -xf CGAL-5.6.tar.xz
for arch in "${ARCHS[@]}"; do
    build_arch "$arch" "$ANDROID_API" ../CGAL-5.6 \
        -DCGAL_HEADER_ONLY=OFF \
        -DWITH_CGAL_Qt5=OFF \
        -DWITH_CGAL_ImageIO=OFF \
        -DWITH_TBB=OFF \
        -DBoost_NO_BOOST_CMAKE=ON \
        -DGMP_INCLUDE_DIR="$INSTALL_DIR/$arch/include" \
        -DGMP_LIBRARIES="$INSTALL_DIR/$arch/lib/libgmp.so" \
        -DMPFR_INCLUDE_DIR="$INSTALL_DIR/$arch/include" \
        -DMPFR_LIBRARIES="$INSTALL_DIR/$arch/lib/libmpfr.so" \
        -DMPFI_INCLUDE_DIR="$INSTALL_DIR/$arch/include" \
        -DMPFI_LIBRARIES="$INSTALL_DIR/$arch/lib/libmpfi.so"
done

# 6. OpenCSG
echo "Building OpenCSG..."
wget -nc https://github.com/OpenSCAD/OpenCSG/archive/refs/tags/v1.4.2.tar.gz -O OpenCSG-1.4.2.tar.gz
tar -xf OpenCSG-1.4.2.tar.gz
for arch in "${ARCHS[@]}"; do
    build_arch "$arch" "$ANDROID_API" ../OpenCSG-1.4.2
done

# 7. Boost (minimal - system, filesystem, thread)
echo "Building Boost..."
wget -nc https://archives.boost.io/release/1.83.0/source/boost_1_83_0.tar.gz
tar -xf boost_1_83_0.tar.gz
cd boost_1_83_0
for arch in "${ARCHS[@]}"; do
    ./bootstrap.sh --with-toolset=clang
    ./b2 -j$NUM_JOBS \
        toolset=clang \
        target-os=android \
        architecture=$(echo $arch | sed 's/-v8a//' | sed 's/armeabi-v7a/arm/') \
        address-model=64 \
        link=shared \
        threading=multi \
        --prefix="$INSTALL_DIR/$arch" \
        --with-system --with-filesystem --with-thread \
        install
done

echo "All dependencies built successfully!"
echo "Install directories:"
for arch in "${ARCHS[@]}"; do
    echo "  $INSTALL_DIR/$arch"
done

# Create a summary for CMake
cat > "$INSTALL_DIR/android_deps.cmake" << EOF
# Auto-generated Android dependencies paths
set(ANDROID_DEPS_ROOT "$INSTALL_DIR")

# GMP
set(GMP_INCLUDE_DIRS "\${ANDROID_DEPS_ROOT}/\${ANDROID_ABI}/include")
set(GMP_LIBRARIES "\${ANDROID_DEPS_ROOT}/\${ANDROID_ABI}/lib/libgmp.so")

# MPFR
set(MPFR_INCLUDE_DIRS "\${ANDROID_DEPS_ROOT}/\${ANDROID_ABI}/include")
set(MPFR_LIBRARIES "\${ANDROID_DEPS_ROOT}/\${ANDROID_ABI}/lib/libmpfr.so")

# MPFI
set(MPFI_INCLUDE_DIRS "\${ANDROID_DEPS_ROOT}/\${ANDROID_ABI}/include")
set(MPFI_LIBRARIES "\${ANDROID_DEPS_ROOT}/\${ANDROID_ABI}/lib/libmpfi.so")

# Eigen3
set(EIGEN3_INCLUDE_DIR "\${ANDROID_DEPS_ROOT}/\${ANDROID_ABI}/include/eigen3")

# CGAL
set(CGAL_DIR "\${ANDROID_DEPS_ROOT}/\${ANDROID_ABI}/lib/cmake/CGAL")

# OpenCSG
set(OPENCSG_INCLUDE_DIR "\${ANDROID_DEPS_ROOT}/\${ANDROID_ABI}/include")
set(OPENCSG_LIBRARY "\${ANDROID_DEPS_ROOT}/\${ANDROID_ABI}/lib/libopencsg.so")

# Boost
set(Boost_INCLUDE_DIR "\${ANDROID_DEPS_ROOT}/\${ANDROID_ABI}/include")
set(Boost_LIBRARY_DIR "\${ANDROID_DEPS_ROOT}/\${ANDROID_ABI}/lib")
set(Boost_SYSTEM_LIBRARY "\${ANDROID_DEPS_ROOT}/\${ANDROID_ABI}/lib/libboost_system.so")
set(Boost_FILESYSTEM_LIBRARY "\${ANDROID_DEPS_ROOT}/\${ANDROID_ABI}/lib/libboost_filesystem.so")
set(Boost_THREAD_LIBRARY "\${ANDROID_DEPS_ROOT}/\${ANDROID_ABI}/lib/libboost_thread.so")
EOF

echo "Created $INSTALL_DIR/android_deps.cmake"
echo "Add this to your CMakeLists.txt: include(\$INSTALL_DIR/android_deps.cmake)"