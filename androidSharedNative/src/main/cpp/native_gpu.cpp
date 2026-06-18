#include "native_common.h"
#include <EGL/egl.h>
#include <GLES2/gl2.h>

#ifndef EGL_OPENGL_ES3_BIT_KHR
#define EGL_OPENGL_ES3_BIT_KHR 0x0040
#endif

namespace {

std::string joinNonEmptyParts(
    const std::string &vendor,
    const std::string &renderer,
    const std::string &version
) {
    std::ostringstream stream;
    if (!vendor.empty()) {
        stream << vendor;
    }
    if (!renderer.empty()) {
        if (!vendor.empty()) {
            stream << " ";
        }
        stream << renderer;
    }
    if (!version.empty()) {
        if (!vendor.empty() || !renderer.empty()) {
            stream << "\n";
        }
        stream << version;
    }
    return stream.str();
}

std::string queryGlesInfo(EGLint renderableType, EGLint clientVersion) {
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display == EGL_NO_DISPLAY) {
        return "";
    }

    EGLint majorVersion = 0;
    EGLint minorVersion = 0;
    if (eglInitialize(display, &majorVersion, &minorVersion) != EGL_TRUE) {
        return "";
    }

    const EGLint configAttributes[] = {
        EGL_RENDERABLE_TYPE, renderableType,
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_NONE
    };

    EGLConfig config = nullptr;
    EGLint configCount = 0;
    if (eglChooseConfig(display, configAttributes, &config, 1, &configCount) != EGL_TRUE || configCount == 0) {
        eglTerminate(display);
        return "";
    }

    const EGLint surfaceAttributes[] = {
        EGL_WIDTH, 1,
        EGL_HEIGHT, 1,
        EGL_NONE
    };
    EGLSurface surface = eglCreatePbufferSurface(display, config, surfaceAttributes);
    if (surface == EGL_NO_SURFACE) {
        eglTerminate(display);
        return "";
    }

    const EGLint contextAttributes[] = {
        EGL_CONTEXT_CLIENT_VERSION, clientVersion,
        EGL_NONE
    };
    EGLContext context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttributes);
    if (context == EGL_NO_CONTEXT) {
        eglDestroySurface(display, surface);
        eglTerminate(display);
        return "";
    }

    if (eglMakeCurrent(display, surface, surface, context) != EGL_TRUE) {
        eglDestroyContext(display, context);
        eglDestroySurface(display, surface);
        eglTerminate(display);
        return "";
    }

    const char *vendorChars = reinterpret_cast<const char *>(glGetString(GL_VENDOR));
    const char *rendererChars = reinterpret_cast<const char *>(glGetString(GL_RENDERER));
    const char *versionChars = reinterpret_cast<const char *>(glGetString(GL_VERSION));

    std::string result = joinNonEmptyParts(
        vendorChars != nullptr ? vendorChars : "",
        rendererChars != nullptr ? rendererChars : "",
        versionChars != nullptr ? versionChars : ""
    );

    eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroyContext(display, context);
    eglDestroySurface(display, surface);
    eglTerminate(display);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_lumkit_tweak_sharednative_GpuInfoBridge_getGlesInfo(JNIEnv *env, jclass) {
    std::string result = queryGlesInfo(EGL_OPENGL_ES3_BIT_KHR, 3);
    if (result.empty()) {
        result = queryGlesInfo(EGL_OPENGL_ES2_BIT, 2);
    }
    return env->NewStringUTF(result.c_str());
}
