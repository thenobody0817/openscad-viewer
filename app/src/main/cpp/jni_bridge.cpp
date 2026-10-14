#include "openscad_wrapper.h"
#include "jni_bridge.h"
#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <memory>

#define LOG_TAG "OpenSCADViewer"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace com::openscad::viewer;

// Helper functions
static std::string jstringToString(JNIEnv* env, jstring jstr) {
    if (!jstr) return "";
    const char* chars = env->GetStringUTFChars(jstr, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(jstr, chars);
    return result;
}

static jstring stringToJstring(JNIEnv* env, const std::string& str) {
    return env->NewStringUTF(str.c_str());
}

static jobjectArray vectorToStringArray(JNIEnv* env, const std::vector<std::string>& vec) {
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray array = env->NewObjectArray(vec.size(), stringClass, nullptr);
    for (size_t i = 0; i < vec.size(); ++i) {
        env->SetObjectArrayElement(array, i, env->NewStringUTF(vec[i].c_str()));
    }
    return array;
}

// OpenSCADWrapper implementation (stub - real implementation needs OpenSCAD built)
namespace com {
namespace openscad {
namespace viewer {

struct OpenSCADWrapper::Impl {
    bool initialized = false;
    std::string libraryPath;
};

OpenSCADWrapper::OpenSCADWrapper() : pImpl(std::make_unique<Impl>()) {}
OpenSCADWrapper::~OpenSCADWrapper() = default;

bool OpenSCADWrapper::initialize(const std::string& libraryPath) {
    LOGD("Initializing OpenSCAD wrapper with library path: %s", libraryPath.c_str());
    pImpl->libraryPath = libraryPath;
    pImpl->initialized = true;
    // TODO: Actually initialize OpenSCAD library
    // This requires OpenSCAD built for Android with all dependencies
    return true;
}

ParseResult OpenSCADWrapper::parseAndEvaluate(const std::string& scadCode,
                                               const std::vector<std::pair<std::string, std::string>>& variables) {
    ParseResult result;
    LOGD("Parsing SCAD code (%zu bytes)", scadCode.size());

    if (!pImpl->initialized) {
        result.success = false;
        result.errorMessage = "OpenSCAD not initialized";
        return result;
    }

    // TODO: Actual OpenSCAD parsing and CSG evaluation
    // For now, return a dummy cube mesh for testing
    result.success = true;
    result.mesh = std::make_unique<Mesh>();

    // Create a simple unit cube centered at origin
    const float s = 10.0f; // 10mm cube
    result.mesh->vertices = {
        // Front face
        {{-s, -s,  s}, {0, 0, 1}, {0, 0}},
        {{ s, -s,  s}, {0, 0, 1}, {1, 0}},
        {{ s,  s,  s}, {0, 0, 1}, {1, 1}},
        {{-s,  s,  s}, {0, 0, 1}, {0, 1}},
        // Back face
        {{-s, -s, -s}, {0, 0, -1}, {0, 0}},
        {{ s, -s, -s}, {0, 0, -1}, {1, 0}},
        {{ s,  s, -s}, {0, 0, -1}, {1, 1}},
        {{-s,  s, -s}, {0, 0, -1}, {0, 1}},
        // Left face
        {{-s, -s, -s}, {-1, 0, 0}, {0, 0}},
        {{-s, -s,  s}, {-1, 0, 0}, {1, 0}},
        {{-s,  s,  s}, {-1, 0, 0}, {1, 1}},
        {{-s,  s, -s}, {-1, 0, 0}, {0, 1}},
        // Right face
        {{ s, -s,  s}, {1, 0, 0}, {0, 0}},
        {{ s, -s, -s}, {1, 0, 0}, {1, 0}},
        {{ s,  s, -s}, {1, 0, 0}, {1, 1}},
        {{ s,  s,  s}, {1, 0, 0}, {0, 1}},
        // Top face
        {{-s,  s,  s}, {0, 1, 0}, {0, 0}},
        {{ s,  s,  s}, {0, 1, 0}, {1, 0}},
        {{ s,  s, -s}, {0, 1, 0}, {1, 1}},
        {{-s,  s, -s}, {0, 1, 0}, {0, 1}},
        // Bottom face
        {{-s, -s, -s}, {0, -1, 0}, {0, 0}},
        {{ s, -s, -s}, {0, -1, 0}, {1, 0}},
        {{ s, -s,  s}, {0, -1, 0}, {1, 1}},
        {{-s, -s,  s}, {0, -1, 0}, {0, 1}},
    };

    result.mesh->indices = {
        0, 1, 2, 2, 3, 0,       // Front
        4, 5, 6, 6, 7, 4,       // Back
        8, 9, 10, 10, 11, 8,    // Left
        12, 13, 14, 14, 15, 12, // Right
        16, 17, 18, 18, 19, 16, // Top
        20, 21, 22, 22, 23, 20  // Bottom
    };

    result.mesh->minX = result.mesh->minY = result.mesh->minZ = -s;
    result.mesh->maxX = result.mesh->maxY = result.mesh->maxZ = s;
    result.triangleCount = result.mesh->indices.size() / 3;
    result.vertexCount = result.mesh->vertices.size();

    // Extract variables from code (simplified)
    // TODO: Real variable extraction from AST
    result.variables = {
        {"size", "number", "10", "10", 1, 100, 1, true},
        {"center", "bool", "true", "true", 0, 1, 1, false},
    };

    return result;
}

std::vector<Variable> OpenSCADWrapper::extractVariables(const std::string& scadCode) {
    std::vector<Variable> vars;
    LOGD("Extracting variables from SCAD code");

    // TODO: Real variable extraction from OpenSCAD AST
    // For now, return dummy variables
    vars = {
        {"size", "number", "10", "10", 1, 100, 1, true},
        {"center", "bool", "true", "true", 0, 1, 1, false},
        {"fn", "number", "32", "32", 3, 128, 1, true},
    };

    return vars;
}

bool OpenSCADWrapper::exportSTL(const Mesh& mesh, const std::string& filepath, bool binary) {
    LOGD("Exporting STL to %s (binary=%d)", filepath.c_str(), binary);
    // TODO: Implement STL export
    return false;
}

bool OpenSCADWrapper::exportOBJ(const Mesh& mesh, const std::string& filepath) {
    LOGD("Exporting OBJ to %s", filepath.c_str());
    // TODO: Implement OBJ export
    return false;
}

std::string OpenSCADWrapper::getVersion() {
    return "OpenSCAD Viewer JNI Wrapper 1.0.0 (OpenSCAD library not linked)";
}

} // namespace viewer
} // namespace openscad
} // namespace com

// JNI Implementation

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_openscad_jni_OpenSCADNative_initialize(JNIEnv* env, jobject thiz, jstring libraryPath) {
    auto* wrapper = new OpenSCADWrapper();
    std::string path = jstringToString(env, libraryPath);
    if (!wrapper->initialize(path)) {
        delete wrapper;
        return 0;
    }
    return reinterpret_cast<jlong>(wrapper);
}

JNIEXPORT void JNICALL
Java_com_openscad_jni_OpenSCADNative_destroy(JNIEnv* env, jobject thiz, jlong handle) {
    auto* wrapper = reinterpret_cast<OpenSCADWrapper*>(handle);
    delete wrapper;
}

JNIEXPORT jobject JNICALL
Java_com_openscad_jni_OpenSCADNative_parseAndEvaluate(JNIEnv* env, jobject thiz,
                                                       jlong handle, jstring scadCode,
                                                       jobjectArray variableNames,
                                                       jobjectArray variableValues) {
    auto* wrapper = reinterpret_cast<OpenSCADWrapper*>(handle);
    if (!wrapper) {
        LOGE("Null wrapper handle");
        return nullptr;
    }

    std::string code = jstringToString(env, scadCode);

    std::vector<std::pair<std::string, std::string>> variables;
    if (variableNames && variableValues) {
        jsize len = env->GetArrayLength(variableNames);
        for (jsize i = 0; i < len; ++i) {
            jstring name = (jstring)env->GetObjectArrayElement(variableNames, i);
            jstring value = (jstring)env->GetObjectArrayElement(variableValues, i);
            variables.emplace_back(jstringToString(env, name), jstringToString(env, value));
            env->DeleteLocalRef(name);
            env->DeleteLocalRef(value);
        }
    }

    ParseResult result = wrapper->parseAndEvaluate(code, variables);

    // Create Java result object
    jclass resultClass = env->FindClass("com/openscad/jni/ParseResult");
    if (!resultClass) {
        LOGE("ParseResult class not found");
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(ZLjava/lang/String;)V");
    jobject resultObj = env->NewObject(resultClass, constructor, result.success,
                                       stringToJstring(env, result.errorMessage));

    if (result.success && result.mesh) {
        // Set mesh data
        jclass meshClass = env->FindClass("com/openscad/jni/Mesh");
        jmethodID meshConstructor = env->GetMethodID(meshClass, "<init>", "()V");
        jobject meshObj = env->NewObject(meshClass, meshConstructor);

        // Set vertices
        jfloatArray vertices = env->NewFloatArray(result.mesh->vertices.size() * 8); // x,y,z,nx,ny,nz,u,v
        jfloat* vertexData = env->GetFloatArrayElements(vertices, nullptr);
        for (size_t i = 0; i < result.mesh->vertices.size(); ++i) {
            const auto& v = result.mesh->vertices[i];
            vertexData[i * 8 + 0] = v.x;
            vertexData[i * 8 + 1] = v.y;
            vertexData[i * 8 + 2] = v.z;
            vertexData[i * 8 + 3] = v.nx;
            vertexData[i * 8 + 4] = v.ny;
            vertexData[i * 8 + 5] = v.nz;
            vertexData[i * 8 + 6] = v.u;
            vertexData[i * 8 + 7] = v.v;
        }
        env->ReleaseFloatArrayElements(vertices, vertexData, 0);

        jmethodID setVertices = env->GetMethodID(meshClass, "setVertices", "([F)V");
        env->CallVoidMethod(meshObj, setVertices, vertices);
        env->DeleteLocalRef(vertices);

        // Set indices
        jintArray indices = env->NewIntArray(result.mesh->indices.size());
        env->SetIntArrayRegion(indices, 0, result.mesh->indices.size(),
                               reinterpret_cast<const jint*>(result.mesh->indices.data()));
        jmethodID setIndices = env->GetMethodID(meshClass, "setIndices", "([I)V");
        env->CallVoidMethod(meshObj, setIndices, indices);
        env->DeleteLocalRef(indices);

        // Set bounds
        jmethodID setBounds = env->GetMethodID(meshClass, "setBounds", "(FFFFFF)V");
        env->CallVoidMethod(meshObj, setBounds,
                           result.mesh->minX, result.mesh->minY, result.mesh->minZ,
                           result.mesh->maxX, result.mesh->maxY, result.mesh->maxZ);

        jmethodID setMesh = env->GetMethodID(resultClass, "setMesh", "(Lcom/openscad/jni/Mesh;)V");
        env->CallVoidMethod(resultObj, setMesh, meshObj);
        env->DeleteLocalRef(meshObj);
        env->DeleteLocalRef(meshClass);
    }

    // Set variables
    if (!result.variables.empty()) {
        jclass varClass = env->FindClass("com/openscad/jni/Variable");
        jmethodID varConstructor = env->GetMethodID(varClass, "<init>",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;FFFFZ)V");

        jobjectArray varArray = env->NewObjectArray(result.variables.size(), varClass, nullptr);
        for (size_t i = 0; i < result.variables.size(); ++i) {
            const auto& v = result.variables[i];
            jobject varObj = env->NewObject(varClass, varConstructor,
                stringToJstring(env, v.name),
                stringToJstring(env, v.type),
                stringToJstring(env, v.value),
                stringToJstring(env, v.defaultValue),
                v.minVal, v.maxVal, v.step, v.isSlider);
            env->SetObjectArrayElement(varArray, i, varObj);
            env->DeleteLocalRef(varObj);
        }

        jmethodID setVariables = env->GetMethodID(resultClass, "setVariables", "([Lcom/openscad/jni/Variable;)V");
        env->CallVoidMethod(resultObj, setVariables, varArray);
        env->DeleteLocalRef(varArray);
        env->DeleteLocalRef(varClass);
    }

    // Set counts
    jmethodID setCounts = env->GetMethodID(resultClass, "setCounts", "(JJ)V");
    env->CallVoidMethod(resultObj, setCounts, result.triangleCount, result.vertexCount);

    env->DeleteLocalRef(resultClass);
    return resultObj;
}

JNIEXPORT jobjectArray JNICALL
Java_com_openscad_jni_OpenSCADNative_extractVariables(JNIEnv* env, jobject thiz,
                                                       jlong handle, jstring scadCode) {
    auto* wrapper = reinterpret_cast<OpenSCADWrapper*>(handle);
    if (!wrapper) return nullptr;

    std::string code = jstringToString(env, scadCode);
    std::vector<Variable> vars = wrapper->extractVariables(code);

    jclass varClass = env->FindClass("com/openscad/jni/Variable");
    jmethodID varConstructor = env->GetMethodID(varClass, "<init>",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;FFFFZ)V");

    jobjectArray varArray = env->NewObjectArray(vars.size(), varClass, nullptr);
    for (size_t i = 0; i < vars.size(); ++i) {
        const auto& v = vars[i];
        jobject varObj = env->NewObject(varClass, varConstructor,
            stringToJstring(env, v.name),
            stringToJstring(env, v.type),
            stringToJstring(env, v.value),
            stringToJstring(env, v.defaultValue),
            v.minVal, v.maxVal, v.step, v.isSlider);
        env->SetObjectArrayElement(varArray, i, varObj);
        env->DeleteLocalRef(varObj);
    }

    env->DeleteLocalRef(varClass);
    return varArray;
}

JNIEXPORT jboolean JNICALL
Java_com_openscad_jni_OpenSCADNative_exportSTL(JNIEnv* env, jobject thiz,
                                                jlong handle, jobject meshObject,
                                                jstring filepath, jboolean binary) {
    // TODO: Implement
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_openscad_jni_OpenSCADNative_exportOBJ(JNIEnv* env, jobject thiz,
                                                jlong handle, jobject meshObject,
                                                jstring filepath) {
    // TODO: Implement
    return JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_openscad_jni_OpenSCADNative_getVersion(JNIEnv* env, jobject thiz) {
    return stringToJstring(env, OpenSCADWrapper::getVersion());
}

} // extern "C"