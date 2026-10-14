# Development notes

## Current state

The engine works and the app renders. What follows is what is actually true as
of `0.1.0-alpha01`, and what is genuinely still missing.

### Working

- **SCAD engine (`scad-core`)** — lexer, parser, AST, evaluator, CSG kernel, 2D
  layer, Customizer parser. 49 unit tests covering all of these.
- **Rendering** — triangles are drawn as filled Compose `Canvas` paths with a
  two-sided Lambert term and an optional edge overlay. Orbit/zoom via pointer
  and pinch input. The camera auto-frames using the mesh's bounding sphere.
- **UI** — Material 3, light/dark, three tabs (viewport / parameters / code /
  console), generated parameter sliders, syntax highlighting.
- **File intake** — `ACTION_VIEW` and `ACTION_SEND` intent filters, handled in
  `MainActivity.handleIncoming`.
- **STL export**.

### Not working / not done

- Export formats other than STL (OBJ, glTF, 3MF).
- Smooth normals, PBR materials, shadows, environment lighting.
- Grid / axes / wireframe overlays.
- Saving back to storage.
- No differential testing against the reference OpenSCAD implementation — the
  parsers are unit-tested but have not been fuzzed against real `.scad` corpora.

## Things that were tried and abandoned

Do not spend time resurrecting these. They are recorded so nobody re-derives
them.

**JNI bridge to OpenSCAD's C++ core.** The original plan was to link
`libopenscad` and call its real parser/evaluator via JNI, with Google Filament
rendering the result. This was abandoned in favour of a pure-Kotlin engine. It
would have required building OpenSCAD's C++ plus CGAL, OpenCSG, GMP, MPFR and
Boost for two Android ABIs — a long, fragile native build for something the app
now does in Kotlin. It also would have forced the whole project under GPLv2
copyleft from OpenSCAD's licence, which is incompatible with the GPLv3 the
project is released under.

The dead `app/src/main/cpp/` stubs were removed in commit history; recover them
with `git checkout caf8f51 -- app/src/main/cpp` if you want to see what they
looked like.

**Google Filament.** A PBR renderer would outclass path rasterisation for heavy
models. It was never integrated — the current renderer draws with `Canvas`
directly and has no native dependencies at all.

## Architecture notes

### Engine (`scad-core/`)

A standalone Kotlin/JVM Gradle build, consumed by the app through a composite
build (`includeBuild("scad-core")` in `settings.gradle.kts`) which substitutes
the `com.openscad:scad-core` coordinate automatically.

Pipeline:

```
source text → ScadLexer → Token[] → ScadParser → ScadAst
           → ScadEvaluator → ScadValue/Solid
           → ScadCsg → ScadGeometry → triangles
```

- `Scad2D` handles 2D primitives and polygon offset/hull/triangulation.
- `ScadCustomizer` extracts variables and `// [n:min:step]` annotations for the
  parameter sliders.
- **JTS** (LocationTech) provides robust polygon overlay, buffering, convex hull
  and triangulation. It is dual-licensed EPL-2.0 / EDL-1.0 and is consumed under
  EDL-1.0, which is compatible with GPLv3.

### Rendering (`app/`)

`RenderMesh` triangulates a `Solid` and computes bounds, centre and radius;
`radius` is the half-diagonal of the axis-aligned bounding box, a conservative
bounding sphere. `MeshViewport` scales by `0.40 / radius`, so a model is framed
from the viewport's smaller dimension. If the camera ever looks wrong, that
constant is the first thing to check.

### Build

Gradle 9.7.1, AGP 9.4.1 with built-in Kotlin support (do not apply
`kotlin.android` — AGP 9 supplies it). `compileSdk` 36, `targetSdk` 37,
`minSdk` 29.

The `kotlin` plugin version in `gradle/libs.versions.toml` and the one used by
`scad-core` are currently mismatched (2.2.10 vs 2.2.20). This works because the
composite build publishes across a module boundary, but it is worth aligning —
see below.

## Known rough edges worth fixing

1. **Align the Kotlin versions** between `app` and `scad-core`. A consumer whose
   Kotlin compiler is older than the metadata version of a dependency will fail
   to read it; this currently only works because the versions are close.
2. **`build-openscad-deps.sh` is dead** — it built native dependencies for the
   abandoned JNI approach. Remove it (done) or delete it outright.
3. **No arm64 device testing.** Everything has only been exercised on x86_64
   under Waydroid. There is no native code, so it *should* be architecture
   independent, but that is an argument rather than evidence.

## Testing

```bash
# engine unit tests (49)
cd scad-core && ../gradlew test

# app build
./gradlew assembleDebug

# lint
./gradlew lint
```

There are no instrumented tests. The UI layer is untested.

## If you want to contribute

The highest-value work, in order:

1. Differential-test the parser against real `.scad` files and report
   divergences. The engine's correctness is the whole product.
2. Smooth normals and a real lighting model.
3. OBJ export.
4. Save/write-back.