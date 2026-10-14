# OpenSCAD Viewer for Android

A native Android app for viewing and editing OpenSCAD (`.scad`) files, with a
live 3D viewport and parametric sliders — on a phone or tablet, with no laptop
and no cloud.

I wanted a tool like this, found nothing that did it, so I built one.

**Status: 0.1.0-alpha01.** It renders real geometry and the parsers are tested,
but treat it as an early build. See [Limitations](#limitations).

## Features

- **A real SCAD engine, written from scratch in Kotlin** — lexer, parser, AST,
  evaluator, CSG kernel and a 2D geometry layer. No OpenSCAD binary, no JNI, no
  native code.
- **Interactive parameters** — sliders are generated from the variables and
  `// [n:min:step]` annotations in your model, and the mesh re-evaluates live as
  you drag them.
- **Syntax-highlighted code editor** alongside the viewport.
- **Orbit camera** — drag to rotate, pinch to zoom, with distance-based scaling
  that keeps the model framed.
- **Two-sided Lambert shading** with optional edge overlay.
- **STL export** of the current mesh.
- **Opens real files** — `.scad` from any file manager via "open with", or shared
  into the app from elsewhere via the share sheet.
- **Material 3 UI**, light and dark, built with Jetpack Compose.

### Language coverage

Primitives `cube` `sphere` `cylinder` `polyhedron` `circle` `square` `polygon`
`text` · CSG `union` `difference` `intersection` `hull` `minkowski` ·
Transforms `translate` `rotate` `scale` `mirror` `resize` `multmatrix` ·
2D/extrusion `offset` `linear_extrude` `rotate_extrude` `projection` `render`
`surface` `import` · Control flow `for` `if` `intersection_for` `let` `assign`
`module` `function` · Plus the standard builtin library: `sin` `cos` `tan`
`atan2` `sqrt` `pow` `exp` `ln` `log` `abs` `sign` `ceil` `floor` `round` `min`
`max` `lookup` `concat` `cross` `len` `str` `rands` and the type predicates
`is_undef` `is_bool` `is_num` `is_string` `is_list`, plus `version`.

## Architecture

```
┌──────────────────────────────────────────────────┐
│  app/ — Jetpack Compose UI                       │
│    MeshViewport (Compose Canvas + Lambert)       │
│    CodePanel · CustomizerPanel · StlExport       │
├──────────────────────────────────────────────────┤
│  scad-core/ — pure Kotlin/JVM engine             │
│    Lexer → Parser → Ast → Evaluator              │
│    Csg · Geometry · 2D · Customizer              │
│    (JTS for robust polygon overlay ops)          │
└──────────────────────────────────────────────────┘
```

There is no GL context, no native library and no C++ in the shipped APK. Meshes
are tessellated by the engine and drawn as filled paths on a Compose `Canvas`,
lit with a two-sided Lambert term. That is a deliberate trade: it keeps the
whole app architecture-independent and the APK small, at the cost of GPU
throughput on very heavy models.

`scad-core` is a Gradle composite build (`includeBuild`) consumed as
`com.openscad:scad-core:0.1.0`. It is deliberately free of Android dependencies,
so it is usable from any JVM project.

## Project structure

```
openscad-viewer/
├── app/                                    # Android application
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/parametric_box.scad       # bundled example model
│       ├── java/com/openscad/viewer/
│       │   ├── MainActivity.kt
│       │   ├── WorkspaceViewModel.kt
│       │   ├── SharedDocument.kt
│       │   ├── ui/                          # App, MeshViewport, CodePanel,
│       │   │                                #   CustomizerPanel, Highlighter, Theme
│       │   ├── render/RenderMesh.kt         # triangulation + bounds
│       │   └── export/StlExport.kt
│       └── res/
├── scad-core/                              # pure Kotlin/JVM engine
│   └── src/{main,test}/kotlin/com/openscad/core/
├── gradle/libs.versions.toml
├── settings.gradle.kts
└── build.gradle.kts
```

## Building

Requirements: **JDK 17** and an **Android SDK** (compileSdk 36). Nothing else —
no NDK, no CMake, no native toolchain.

```bash
# point the build at your SDK
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # release APK (signed; see below)
./gradlew test                 # scad-core unit tests (49 tests)
```

`local.properties` is machine-specific and is not tracked in git.

## Tests

The engine carries 49 unit tests across the lexer, customizer parser, evaluator,
geometry kernel and 2D layer:

```bash
cd scad-core && ../gradlew test
```

## Signing

Release builds are signed with a key that is **published in this repository**
(`keystore/`). This is intentional and is what makes the GPL real rather than
decorative on Android: anyone may rebuild the app, re-sign it with that key, and
install their own build on their own device.

```bash
./gradlew assembleRelease
adb install app/build/outputs/apk/release/app-release.apk
```

## Limitations

Honest list of what is *not* finished:

- **Alpha.** Rendering and parsing work; edge cases are untested against the
  reference OpenSCAD implementation.
- **Export is STL only.** OBJ, glTF and 3MF are not implemented.
- **Flat-shaded only.** No smooth normals, no PBR materials, no shadows.
- **Large models will be slow.** Path rasterisation on the CPU is not a
  substitute for a real GPU pipeline.
- **No save / write-back.** Files can be opened and shared in, but there is no
  in-app file browser and no way to write a modified model back to storage.
- **Tested on x86_64** (Waydroid). Not yet exercised on an arm64 physical device.

## License

**GPL-3.0-only.** See [LICENSE](LICENSE).

This project contains no OpenSCAD source code and does not link against
OpenSCAD's C++ library, so GPL copyleft obligations from OpenSCAD do not apply.
The `.scad` language itself is implemented from its published description.

## Third-party components

| Component | License | Use |
|---|---|---|
| [JTS](https://github.com/locationtech/jts) (LocationTech) | EPL-2.0 / EDL-1.0 | Robust polygon overlay, buffering, convex hull, triangulation |
| Kotlin stdlib | Apache-2.0 | Runtime |
| AndroidX, Jetpack Compose | Apache-2.0 | UI, lifecycle |

JTS is consumed under its EDL-1.0 option, which is compatible with GPLv3.

## Acknowledgments

- [OpenSCAD](https://openscad.org/) — for the language this app implements.
  Not affiliated with or endorsed by the OpenSCAD project.
- [LocationTech JTS](https://github.com/locationtech/jts) — the 2D geometry kernel.