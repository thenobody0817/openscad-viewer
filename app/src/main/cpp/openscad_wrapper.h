#ifndef OPENSCAD_WRAPPER_H
#define OPENSCAD_WRAPPER_H

#include <string>
#include <vector>
#include <memory>
#include <jni.h>

// Forward declarations for OpenSCAD internals
namespace openscad {
    class CSG;
    class Geometry;
    class Variable;
}

namespace com {
namespace openscad {
namespace viewer {

struct Vertex {
    float x, y, z;
    float nx, ny, nz;
    float u, v;
};

struct Mesh {
    std::vector<Vertex> vertices;
    std::vector<uint32_t> indices;
    float minX, minY, minZ;
    float maxX, maxY, maxZ;
};

struct Variable {
    std::string name;
    std::string type;  // "number", "string", "bool", "vector"
    std::string value;
    std::string defaultValue;
    float minVal = 0.0f;
    float maxVal = 100.0f;
    float step = 1.0f;
    bool isSlider = false;
};

struct ParseResult {
    bool success = false;
    std::string errorMessage;
    std::unique_ptr<Mesh> mesh;
    std::vector<Variable> variables;
    size_t triangleCount = 0;
    size_t vertexCount = 0;
};

class OpenSCADWrapper {
public:
    OpenSCADWrapper();
    ~OpenSCADWrapper();

    // Initialize OpenSCAD library (load builtins, libraries, etc.)
    bool initialize(const std::string& libraryPath);

    // Parse and evaluate a .scad file
    ParseResult parseAndEvaluate(const std::string& scadCode,
                                  const std::vector<std::pair<std::string, std::string>>& variables = {});

    // Parse only (extract variables without rendering)
    std::vector<Variable> extractVariables(const std::string& scadCode);

    // Export mesh to STL (binary)
    bool exportSTL(const Mesh& mesh, const std::string& filepath, bool binary = true);

    // Export mesh to OBJ
    bool exportOBJ(const Mesh& mesh, const std::string& filepath);

    // Get library version
    static std::string getVersion();

private:
    struct Impl;
    std::unique_ptr<Impl> pImpl;
};

} // namespace viewer
} // namespace openscad
} // namespace com

#endif // OPENSCAD_WRAPPER_H