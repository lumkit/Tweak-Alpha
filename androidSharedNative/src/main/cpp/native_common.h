#pragma once

#include <dirent.h>
#include <cerrno>
#include <fcntl.h>
#include <jni.h>
#include <sstream>
#include <string>
#include <sys/stat.h>
#include <unistd.h>
#include <vector>
#include <algorithm>

class ScopedUtfChars {
public:
    ScopedUtfChars(JNIEnv *env, jstring value) : env_(env), value_(value) {
        if (value_ != nullptr) {
            chars_ = env_->GetStringUTFChars(value_, nullptr);
        }
    }

    ~ScopedUtfChars() {
        if (chars_ != nullptr) {
            env_->ReleaseStringUTFChars(value_, chars_);
        }
    }

    [[nodiscard]] const char *get() const {
        return chars_;
    }

private:
    JNIEnv *env_;
    jstring value_;
    const char *chars_ = nullptr;
};

[[noreturn]] inline void throwIOException(JNIEnv *env, const std::string &message) {
    jclass exceptionClass = env->FindClass("java/io/IOException");
    env->ThrowNew(exceptionClass, message.c_str());
    throw 0;
}

inline std::string lastError(const std::string &prefix) {
    std::ostringstream stream;
    stream << prefix << ": " << strerror(errno);
    return stream.str();
}

inline std::string requirePath(JNIEnv *env, jstring value, const char *label) {
    if (value == nullptr) {
        throwIOException(env, std::string(label) + " must not be null");
    }
    ScopedUtfChars chars(env, value);
    if (chars.get() == nullptr) {
        throwIOException(env, std::string("Unable to decode ") + label);
    }
    return chars.get();
}

inline std::string joinPath(const std::string &parent, const std::string &child) {
    if (parent.empty() || parent == "/") {
        return parent == "/" ? "/" + child : child;
    }
    if (parent.back() == '/') {
        return parent + child;
    }
    return parent + "/" + child;
}

inline bool pathExists(const std::string &path) {
    struct stat status{};
    return lstat(path.c_str(), &status) == 0;
}
