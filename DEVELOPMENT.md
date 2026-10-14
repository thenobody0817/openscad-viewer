# OpenSCAD Viewer - Development Notes

## Current Status

This is a **foundation/skeleton** project. The key infrastructure is in place but the OpenSCAD C++ library integration needs to be completed.

### What's Working
- ✅ Android project structure with Gradle, Hilt, Room, Compose
- ✅ JNI bridge framework (Java/Kotlin + C++)
- ✅ Filament rendering integration
- ✅ UI screens: Editor, Viewer, Parameters
- ✅ ViewModels with state management
- ✅ Database schema for files/preferences
- ✅ Dependency build script for native libraries

### What Needs Implementation

#### 1. OpenSCAD C++ Library Build (Critical)
The `build-openscad-deps.sh` script builds dependencies but you still need to:
- Build OpenSCAD itself for Android (disable GUI, enable library)
- Link against the prebuilt dependencies
- Produce `libopenscad.so` for each architecture

#### 2. JNI Bridge Implementation (Critical)
In `app/src/main/cpp/jni_bridge.cpp`:
- Replace stub `OpenSCADWrapper` with real OpenSCAD API calls
- Implement `parseAndEvaluate()` using OpenSCAD's `Parser`, `Evaluator`, `CSG` classes
- Implement `extractVariables()` by walking the AST
- Implement `exportSTL()/exportOBJ()` using OpenSCAD's exporters

#### 3. Filament Rendering (Partial)
- Basic mesh rendering works
- Need: Grid, axes, selection highlighting, wireframe mode
- Need: Proper lighting setup (HDRI environment maps)
- Need: Touch gesture handling refinement

#### 4. Code Editor (Basic)
- Current: Basic `BasicTextField`
- Need: Syntax highlighting (consider `compose-highlight` or custom)
- Need: Line numbers, error markers, autocomplete

#### 5. Export Functionality
- STL/OBJ export from Filament mesh data
- glTF export using `gltfio`

## Next Steps

### Week 1: OpenSCAD Library
1. Run `build-openscad-deps.sh` (takes 30-60 min)
2. Build OpenSCAD for Android (modify CMakeLists to use prebuilt deps)
3. Copy `.so` files to `app/src/main/jnilibs/`

### Week 2: JNI Integration
1. Study OpenSCAD's `src/` - key files: `parser/parser.cc`, `evaluator/evaluator.cc`, `geometry/csg.cc`
2. Implement `OpenSCADWrapper::parseAndEvaluate()` using OpenSCAD API
3. Implement variable extraction from AST
4. Test with simple .scad files

### Week 3: Polish Rendering
1. Add environment map lighting
2. Implement grid/axes overlay
3. Add model selection/highlighting
4. Optimize render loop

### Week 4: Editor & Export
1. Add syntax highlighting
2. Implement STL/OBJ export
3. Add file management (open/save/recent)
4. Settings screen

## OpenSCAD C++ API Notes

Key classes to use:
```cpp
// Parsing
openscad::Parser parser;
openscad::AST::Node* ast = parser.parse(scad_code);

// Evaluation
openscad::Evaluator evaluator;
openscad::CSG* csg = evaluator.eval(ast);

// Variable extraction
// Walk AST looking for VariableAssignment nodes

// Mesh generation
openscad::Geometry geometry;
csg->render(geometry);
// Extract triangles from geometry

// Export
openscad::exportSTL(geometry, filepath);
```

## Filament Notes

Key patterns:
```cpp
// Create material
Material* mat = Material::Builder()
    .packageName("com.google.android.filament")
    .name("lit")
    .build(engine);

// Create mesh
VertexBuffer* vb = VertexBuffer::Builder()
    .vertexCount(count)
    .attribute(POSITION, 0, FLOAT3, 0, stride)
    .attribute(NORMAL, 0, FLOAT3, 12, stride)
    .attribute(UV0, 0, FLOAT2, 24, stride)
    .build(engine);

// Render loop
engine->beginFrame(swapChain);
engine->render(view);
engine->endFrame();
```

## Testing

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires device)
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

## Troubleshooting

### JNI UnsatisfiedLinkError
- Check `jnilibs/<arch>/` has all required `.so` files
- Verify `System.loadLibrary("openscad_jni")` matches `libopenscad_jni.so`
- Check logcat for missing dependencies

### Filament Crashes
- Ensure `Filament.init()` called before any Filament code
- Check OpenGL ES version (requires 3.2+)
- Verify swap chain creation matches surface size

### Build Errors
- Clean: `./gradlew clean`
- Delete `.gradle`, `build` directories
- Invalidate caches in Android Studio