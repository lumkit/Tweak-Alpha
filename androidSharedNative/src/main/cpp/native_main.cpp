#include <android/log.h>
#include <jni.h>
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

    bool isAllowedPackage(const std::string &packageName) {
        if (packageName.empty()) {
            return false;
        }
        if (packageName == kAllowedPackagePrefix) {
            return true;
        }
        const std::string allowedPrefix = std::string(kAllowedPackagePrefix) + ".";
        return packageName.rfind(allowedPrefix, 0) == 0;
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
    if (!isAllowedPackage(packageName)) {
        __android_log_print(
                ANDROID_LOG_ERROR,
                kLogTag,
                "Illegal native load request, package=%s",
                packageName.empty() ? "<unknown>" : packageName.c_str()
        );
        throwSecurityException(env, "Illegal package for tweak_shared_native: " + packageName);
        exit(-1);
    }

    __android_log_print(
            ANDROID_LOG_INFO,
            kLogTag,
            "Native library loaded by package=%s",
            packageName.c_str()
    );
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM * /*vm*/, void * /*reserved*/) {
}
