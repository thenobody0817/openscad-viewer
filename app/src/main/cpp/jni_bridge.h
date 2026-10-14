#ifndef JNI_BRIDGE_H
#define JNI_BRIDGE_H

#include <jni.h>
#include "openscad_wrapper.h"

#ifdef __cplusplus
extern "C" {
#endif

// OpenSCAD JNI functions
JNIEXPORT jlong JNICALL
Java_com_openscad_jni_OpenSCADNative_initialize(JNIEnv* env, jobject thiz, jstring libraryPath);

JNIEXPORT void JNICALL
Java_com_openscad_jni_OpenSCADNative_destroy(JNIEnv* env, jobject thiz, jlong handle);

JNIEXPORT jobject JNICALL
Java_com_openscad_jni_OpenSCADNative_parseAndEvaluate(JNIEnv* env, jobject thiz,
                                                       jlong handle, jstring scadCode,
                                                       jobjectArray variableNames,
                                                       jobjectArray variableValues);

JNIEXPORT jobjectArray JNICALL
Java_com_openscad_jni_OpenSCADNative_extractVariables(JNIEnv* env, jobject thiz,
                                                       jlong handle, jstring scadCode);

JNIEXPORT jboolean JNICALL
Java_com_openscad_jni_OpenSCADNative_exportSTL(JNIEnv* env, jobject thiz,
                                                jlong handle, jobject meshObject,
                                                jstring filepath, jboolean binary);

JNIEXPORT jboolean JNICALL
Java_com_openscad_jni_OpenSCADNative_exportOBJ(JNIEnv* env, jobject thiz,
                                                jlong handle, jobject meshObject,
                                                jstring filepath);

JNIEXPORT jstring JNICALL
Java_com_openscad_jni_OpenSCADNative_getVersion(JNIEnv* env, jobject thiz);

// Viewer JNI functions (mesh rendering helpers)
JNIEXPORT jlong JNICALL
Java_com_openscad_viewer_render_FilamentJNI_createEngine(JNIEnv* env, jobject thiz);

JNIEXPORT void JNICALL
Java_com_openscad_viewer_render_FilamentJNI_destroyEngine(JNIEnv* env, jobject thiz, jlong engine);

JNIEXPORT jlong JNICALL
Java_com_openscad_viewer_render_FilamentJNI_createScene(JNIEnv* env, jobject thiz, jlong engine);

JNIEXPORT void JNICALL
Java_com_openscad_viewer_render_FilamentJNI_destroyScene(JNIEnv* env, jobject thiz, jlong engine, jlong scene);

JNIEXPORT jlong JNICALL
Java_com_openscad_viewer_render_FilamentJNI_createMesh(JNIEnv* env, jobject thiz,
                                                        jlong engine, jfloatArray vertices,
                                                        jfloatArray normals, jfloatArray uvs,
                                                        jintArray indices, jint vertexCount,
                                                        jint indexCount);

JNIEXPORT void JNICALL
Java_com_openscad_viewer_render_FilamentJNI_render(JNIEnv* env, jobject thiz,
                                                    jlong engine, jlong scene, jlong view,
                                                    jlong swapChain);

JNIEXPORT void JNICALL
Java_com_openscad_viewer_render_FilamentJNI_setCamera(JNIEnv* env, jobject thiz,
                                                       jlong view, jfloat eyeX, jfloat eyeY, jfloat eyeZ,
                                                       jfloat centerX, jfloat centerY, jfloat centerZ,
                                                       jfloat upX, jfloat upY, jfloat upZ);

#ifdef __cplusplus
}
#endif

#endif // JNI_BRIDGE_H