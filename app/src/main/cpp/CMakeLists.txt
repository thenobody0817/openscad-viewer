cmake_minimum_required(VERSION 3.22.1)
project("openscad-viewer" LANGUAGES CXX)

# C++20 standard
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Build configuration
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# Android-specific settings
if(ANDROID)
    set(CMAKE_ANDROID_STL c++_shared)
    set(CMAKE_ANDROID_NDK_TOOLCHAIN_VERSION clang)
endif()

# Output directories
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/../../app/src/main/jnilibs/${ANDROID_ABI})
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/../../app/src/main/jnilibs/${ANDROID_ABI})

# Include directories
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Find OpenSCAD dependencies (CGAL, OpenCSG, MPFR, GMP, Eigen, etc.)
# These need to be built for Android first - see build-openscad-deps.sh

# OpenSCAD library
# We'll create a wrapper that uses OpenSCAD's C++ API

# Filament is loaded via Gradle dependency (AAR), no need to link here

# Main JNI library
add_library(openscad_viewer_jni SHARED
    src/main/cpp/jni_bridge.cpp
    src/main/cpp/openscad_wrapper.cpp
    src/main/cpp/mesh_generator.cpp
    src/main/cpp/export_stl.cpp
    src/main/cpp/export_obj.cpp
)

# Link libraries
target_link_libraries(openscad_viewer_jni
    android
    log
    m
    dl
    # OpenSCAD libraries (to be provided)
    # openscad
    # cgal
    # opencsg
    # mpfr
    # gmp
    # boost_system
    # boost_filesystem
    # boost_thread
    # eigen3
)

# Install headers
install(FILES
    src/main/cpp/openscad_wrapper.h
    src/main/cpp/jni_bridge.h
    DESTINATION include
)