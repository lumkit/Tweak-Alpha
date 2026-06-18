#include "native_common.h"
#include <sys/statvfs.h>

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_lumkit_tweak_sharednative_StorageBridge_getTotalBytes(JNIEnv *env, jclass, jstring path) {
    try {
        std::string resolvedPath = requirePath(env, path, "path");
        struct statvfs stat{};
        if (statvfs(resolvedPath.c_str(), &stat) != 0) {
            throwIOException(env, lastError("Failed to statvfs " + resolvedPath));
        }
        return static_cast<jlong>(stat.f_blocks) * static_cast<jlong>(stat.f_frsize);
    } catch (...) {
        return -1;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_lumkit_tweak_sharednative_StorageBridge_getUsedBytes(JNIEnv *env, jclass, jstring path) {
    try {
        std::string resolvedPath = requirePath(env, path, "path");
        struct statvfs stat{};
        if (statvfs(resolvedPath.c_str(), &stat) != 0) {
            throwIOException(env, lastError("Failed to statvfs " + resolvedPath));
        }
        jlong total = static_cast<jlong>(stat.f_blocks) * static_cast<jlong>(stat.f_frsize);
        jlong free = static_cast<jlong>(stat.f_bfree) * static_cast<jlong>(stat.f_frsize);
        return total - free;
    } catch (...) {
        return -1;
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_lumkit_tweak_sharednative_StorageBridge_getUserProfiles(JNIEnv *env, jclass) {
    try {
        std::string userDir = "/data/user";
        std::vector<std::string> profiles;

        DIR *directory = opendir(userDir.c_str());
        if (directory != nullptr) {
            while (dirent *entry = readdir(directory)) {
                std::string name = entry->d_name;
                if (name == "." || name == "..") continue;
                bool isNumeric = !name.empty();
                for (char c : name) {
                    if (c < '0' || c > '9') { isNumeric = false; break; }
                }
                if (isNumeric) {
                    profiles.push_back(name);
                }
            }
            closedir(directory);
        }

        std::sort(profiles.begin(), profiles.end(), [](const std::string &a, const std::string &b) {
            return std::stoi(a) < std::stoi(b);
        });

        jclass stringClass = env->FindClass("java/lang/String");
        jobjectArray array = env->NewObjectArray(static_cast<jsize>(profiles.size()), stringClass, nullptr);
        for (jsize i = 0; i < static_cast<jsize>(profiles.size()); i++) {
            jstring value = env->NewStringUTF(profiles[static_cast<size_t>(i)].c_str());
            env->SetObjectArrayElement(array, i, value);
            env->DeleteLocalRef(value);
        }
        return array;
    } catch (...) {
        jclass stringClass = env->FindClass("java/lang/String");
        return env->NewObjectArray(0, stringClass, nullptr);
    }
}
