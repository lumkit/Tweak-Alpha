#include <android/log.h>
#include <jni.h>
#include <cstdio>
#include <string>

namespace {

    constexpr char kLogTag[] = "TweakNative";
    constexpr char kAllowedPackagePrefix[] = "io.github.lumkit.tweak";
    void clearPendingException(JNIEnv *env) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }

    std::string toString(JNIEnv *env, jstring value) {
        if (value == nullptr) {
            return {};
        }
        const char *chars = env->GetStringUTFChars(value, nullptr);
        if (chars == nullptr) {
            clearPendingException(env);
            return {};
        }
        std::string result(chars);
        env->ReleaseStringUTFChars(value, chars);
        return result;
    }

    std::string callStaticStringMethod(
            JNIEnv *env,
            const char *className,
            const char *methodName
    ) {
        jclass clazz = env->FindClass(className);
        if (clazz == nullptr) {
            clearPendingException(env);
            return {};
        }
        jmethodID method = env->GetStaticMethodID(clazz, methodName, "()Ljava/lang/String;");
        if (method == nullptr) {
            clearPendingException(env);
            env->DeleteLocalRef(clazz);
            return {};
        }
        auto value = static_cast<jstring>(env->CallStaticObjectMethod(clazz, method));
        if (env->ExceptionCheck()) {
            clearPendingException(env);
            env->DeleteLocalRef(clazz);
            return {};
        }
        std::string result = toString(env, value);
        if (value != nullptr) {
            env->DeleteLocalRef(value);
        }
        env->DeleteLocalRef(clazz);
        return result;
    }

    std::string getPackageNameFromApplication(JNIEnv *env) {
        jclass activityThreadClass = env->FindClass("android/app/ActivityThread");
        if (activityThreadClass == nullptr) {
            clearPendingException(env);
            return {};
        }
        jmethodID currentApplicationMethod = env->GetStaticMethodID(
                activityThreadClass,
                "currentApplication",
                "()Landroid/app/Application;"
        );
        if (currentApplicationMethod == nullptr) {
            clearPendingException(env);
            env->DeleteLocalRef(activityThreadClass);
            return {};
        }
        jobject application = env->CallStaticObjectMethod(
                activityThreadClass,
                currentApplicationMethod
        );
        if (env->ExceptionCheck()) {
            clearPendingException(env);
            env->DeleteLocalRef(activityThreadClass);
            return {};
        }
        if (application == nullptr) {
            env->DeleteLocalRef(activityThreadClass);
            return {};
        }
        jclass contextClass = env->FindClass("android/content/Context");
        if (contextClass == nullptr) {
            clearPendingException(env);
            env->DeleteLocalRef(application);
            env->DeleteLocalRef(activityThreadClass);
            return {};
        }
        jmethodID getPackageNameMethod = env->GetMethodID(
                contextClass,
                "getPackageName",
                "()Ljava/lang/String;"
        );
        if (getPackageNameMethod == nullptr) {
            clearPendingException(env);
            env->DeleteLocalRef(contextClass);
            env->DeleteLocalRef(application);
            env->DeleteLocalRef(activityThreadClass);
            return {};
        }
        auto packageName = static_cast<jstring>(env->CallObjectMethod(application, getPackageNameMethod));
        if (env->ExceptionCheck()) {
            clearPendingException(env);
            env->DeleteLocalRef(contextClass);
            env->DeleteLocalRef(application);
            env->DeleteLocalRef(activityThreadClass);
            return {};
        }
        std::string result = toString(env, packageName);
        if (packageName != nullptr) {
            env->DeleteLocalRef(packageName);
        }
        env->DeleteLocalRef(contextClass);
        env->DeleteLocalRef(application);
        env->DeleteLocalRef(activityThreadClass);
        return result;
    }

    std::string resolveCurrentPackageName(JNIEnv *env) {
        std::string packageName = getPackageNameFromApplication(env);
        if (!packageName.empty()) {
            return packageName;
        }
        packageName = callStaticStringMethod(env, "android/app/ActivityThread", "currentPackageName");
        if (!packageName.empty()) {
            return packageName;
        }
        packageName = callStaticStringMethod(env, "android/app/ActivityThread", "currentOpPackageName");
        if (!packageName.empty()) {
            return packageName;
        }
        return callStaticStringMethod(env, "android/app/ActivityThread", "currentProcessName");
    }

    std::string readProcCmdline() {
        FILE *file = std::fopen("/proc/self/cmdline", "rb");
        if (file == nullptr) {
            return {};
        }
        std::string result;
        char buffer[256];
        while (const size_t read = std::fread(buffer, 1, sizeof(buffer), file)) {
            result.append(buffer, read);
        }
        std::fclose(file);
        for (char &ch : result) {
            if (ch == '\0') {
                ch = ' ';
            }
        }
        return result;
    }

    bool hasAllowedPrefix(const std::string &value) {
        if (value.empty()) {
            return false;
        }
        if (value == kAllowedPackagePrefix) {
            return true;
        }
        const std::string allowedPrefix = std::string(kAllowedPackagePrefix) + ".";
        const std::string allowedProcessPrefix = std::string(kAllowedPackagePrefix) + ":";
        return value.rfind(allowedPrefix, 0) == 0 || value.rfind(allowedProcessPrefix, 0) == 0;
    }

    bool isAllowedPackage(const std::string &packageName) {
        return hasAllowedPrefix(packageName);
    }

    bool isAllowedProcess(const std::string &processInfo) {
        return hasAllowedPrefix(processInfo);
    }

    void throwSecurityException(JNIEnv *env, const std::string &message) {
        jclass exceptionClass = env->FindClass("java/lang/SecurityException");
        if (exceptionClass == nullptr) {
            clearPendingException(env);
            return;
        }
        env->ThrowNew(exceptionClass, message.c_str());
        env->DeleteLocalRef(exceptionClass);
    }

}  // namespace

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void * /*reserved*/) {
    JNIEnv *env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK || env == nullptr) {
        return JNI_ERR;
    }

    const std::string packageName = resolveCurrentPackageName(env);
    const std::string processInfo = readProcCmdline();
    const bool allowed = isAllowedPackage(packageName) || isAllowedProcess(processInfo);
    if (!allowed) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                kLogTag,
                "Illegal native load request, package=%s, process=%s",
                packageName.empty() ? "<unknown>" : packageName.c_str(),
                processInfo.empty() ? "<empty>" : processInfo.c_str()
        );
        throwSecurityException(env, "Illegal package for tweak_shared_native: " + packageName);
        return JNI_ERR;
    }

    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "Native library loaded by package=%s, process=%s",
            packageName.c_str(),
            processInfo.empty() ? "<empty>" : processInfo.c_str()
    );
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM * /*vm*/, void * /*reserved*/) {
}
