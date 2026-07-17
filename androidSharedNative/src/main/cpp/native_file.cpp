#include "native_common.h"
#include <limits.h>

namespace {

struct stat requireStatus(JNIEnv *env, const std::string &path) {
    struct stat status{};
    if (lstat(path.c_str(), &status) != 0) {
        throwIOException(env, lastError("Failed to stat " + path));
    }
    return status;
}

void ensureParentDirectory(JNIEnv *env, const std::string &path) {
    auto separatorIndex = path.find_last_of('/');
    if (separatorIndex == std::string::npos || separatorIndex == 0) {
        return;
    }
    std::string parent = path.substr(0, separatorIndex);
    struct stat status{};
    if (lstat(parent.c_str(), &status) == 0) {
        if (!S_ISDIR(status.st_mode)) {
            throwIOException(env, parent + " is not a directory");
        }
        return;
    }
    if (errno != ENOENT) {
        throwIOException(env, lastError("Failed to stat " + parent));
    }

    std::string current;
    if (!parent.empty() && parent.front() == '/') {
        current = "/";
    }
    std::stringstream stream(parent);
    std::string part;
    while (std::getline(stream, part, '/')) {
        if (part.empty()) {
            continue;
        }
        current = joinPath(current, part);
        if (mkdir(current.c_str(), 0777) != 0 && errno != EEXIST) {
            throwIOException(env, lastError("Failed to create directory " + current));
        }
    }
}

std::vector<unsigned char> readFileBytes(JNIEnv *env, const std::string &path) {
    int fd = open(path.c_str(), O_RDONLY);
    if (fd < 0) {
        throwIOException(env, lastError("Failed to open " + path));
    }

    std::vector<unsigned char> data;
    unsigned char buffer[8192];
    while (true) {
        ssize_t readCount = read(fd, buffer, sizeof(buffer));
        if (readCount == 0) {
            break;
        }
        if (readCount < 0) {
            close(fd);
            throwIOException(env, lastError("Failed to read " + path));
        }
        data.insert(data.end(), buffer, buffer + readCount);
    }
    close(fd);
    return data;
}

void writeFileBytes(JNIEnv *env, const std::string &path, const unsigned char *bytes, size_t size) {
    ensureParentDirectory(env, path);
    int fd = open(path.c_str(), O_WRONLY | O_CREAT | O_TRUNC, 0666);
    if (fd < 0) {
        throwIOException(env, lastError("Failed to open " + path));
    }

    size_t offset = 0;
    while (offset < size) {
        ssize_t written = write(fd, bytes + offset, size - offset);
        if (written < 0) {
            close(fd);
            throwIOException(env, lastError("Failed to write " + path));
        }
        offset += static_cast<size_t>(written);
    }

    if (close(fd) != 0) {
        throwIOException(env, lastError("Failed to close " + path));
    }
}

std::vector<std::string> listDirectory(JNIEnv *env, const std::string &path) {
    DIR *directory = opendir(path.c_str());
    if (directory == nullptr) {
        throwIOException(env, lastError("Failed to open directory " + path));
    }

    std::vector<std::string> children;
    while (dirent *entry = readdir(directory)) {
        std::string name = entry->d_name;
        if (name == "." || name == "..") {
            continue;
        }
        children.push_back(joinPath(path, name));
    }
    closedir(directory);
    std::sort(children.begin(), children.end());
    return children;
}

void deleteRecursively(JNIEnv *env, const std::string &path, bool recursive) {
    struct stat status = requireStatus(env, path);
    if (S_ISDIR(status.st_mode) && !S_ISLNK(status.st_mode)) {
        if (recursive) {
            for (const auto &child : listDirectory(env, path)) {
                deleteRecursively(env, child, true);
            }
        }
        if (rmdir(path.c_str()) != 0) {
            throwIOException(env, lastError("Failed to remove directory " + path));
        }
        return;
    }

    if (unlink(path.c_str()) != 0) {
        throwIOException(env, lastError("Failed to remove " + path));
    }
}

void copyRegularFile(JNIEnv *env, const std::string &sourcePath, const std::string &targetPath, bool overwrite, mode_t mode) {
    if (!overwrite && pathExists(targetPath)) {
        errno = EEXIST;
        throwIOException(env, lastError("Target already exists " + targetPath));
    }
    if (overwrite && pathExists(targetPath)) {
        deleteRecursively(env, targetPath, true);
    }

    auto data = readFileBytes(env, sourcePath);
    writeFileBytes(env, targetPath, data.data(), data.size());
    if (chmod(targetPath.c_str(), mode) != 0) {
        throwIOException(env, lastError("Failed to preserve mode for " + targetPath));
    }
}

void copySymbolicLink(JNIEnv *env, const std::string &sourcePath, const std::string &targetPath, bool overwrite) {
    if (!overwrite && pathExists(targetPath)) {
        errno = EEXIST;
        throwIOException(env, lastError("Target already exists " + targetPath));
    }
    if (overwrite && pathExists(targetPath)) {
        deleteRecursively(env, targetPath, true);
    }

    std::vector<char> buffer(PATH_MAX, '\0');
    ssize_t length = readlink(sourcePath.c_str(), buffer.data(), buffer.size() - 1);
    if (length < 0) {
        throwIOException(env, lastError("Failed to read symbolic link " + sourcePath));
    }
    buffer[static_cast<size_t>(length)] = '\0';
    ensureParentDirectory(env, targetPath);
    if (symlink(buffer.data(), targetPath.c_str()) != 0) {
        throwIOException(env, lastError("Failed to create symbolic link " + targetPath));
    }
}

void copyRecursively(JNIEnv *env, const std::string &sourcePath, const std::string &targetPath, bool overwrite) {
    struct stat sourceStatus = requireStatus(env, sourcePath);

    if (S_ISLNK(sourceStatus.st_mode)) {
        copySymbolicLink(env, sourcePath, targetPath, overwrite);
        return;
    }

    if (S_ISDIR(sourceStatus.st_mode)) {
        if (pathExists(targetPath)) {
            struct stat targetStatus = requireStatus(env, targetPath);
            if (!S_ISDIR(targetStatus.st_mode)) {
                if (!overwrite) {
                    errno = EEXIST;
                    throwIOException(env, lastError("Target already exists " + targetPath));
                }
                deleteRecursively(env, targetPath, true);
            }
        }

        ensureParentDirectory(env, targetPath);
        if (mkdir(targetPath.c_str(), sourceStatus.st_mode & 0777) != 0 && errno != EEXIST) {
            throwIOException(env, lastError("Failed to create directory " + targetPath));
        }

        for (const auto &child : listDirectory(env, sourcePath)) {
            std::string childName = child.substr(sourcePath.size() + 1);
            copyRecursively(env, child, joinPath(targetPath, childName), overwrite);
        }
        if (chmod(targetPath.c_str(), sourceStatus.st_mode & 0777) != 0) {
            throwIOException(env, lastError("Failed to preserve mode for " + targetPath));
        }
        return;
    }

    copyRegularFile(env, sourcePath, targetPath, overwrite, sourceStatus.st_mode & 0777);
}

mode_t parseMode(JNIEnv *env, const std::string &modeString) {
    char *endPointer = nullptr;
    long parsed = strtol(modeString.c_str(), &endPointer, 8);
    if (endPointer == nullptr || *endPointer != '\0' || parsed < 0 || parsed > 07777) {
        throwIOException(env, "Invalid chmod mode: " + modeString);
    }
    return static_cast<mode_t>(parsed);
}

/**
 * 计算路径占用大小：普通文件返回 st_size；目录递归累加子项；不跟随符号链接。
 */
jlong calculateLength(JNIEnv *env, const std::string &path) {
    struct stat status = requireStatus(env, path);
    if (S_ISLNK(status.st_mode)) {
        return static_cast<jlong>(status.st_size);
    }
    if (S_ISDIR(status.st_mode)) {
        jlong total = 0;
        for (const auto &child : listDirectory(env, path)) {
            total += calculateLength(env, child);
        }
        return total;
    }
    return static_cast<jlong>(status.st_size);
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_lumkit_tweak_sharednative_NativeFileBridge_exists(JNIEnv *env, jclass, jstring path) {
    try {
        return static_cast<jboolean>(pathExists(requirePath(env, path, "path")));
    } catch (...) {
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_io_github_lumkit_tweak_sharednative_NativeFileBridge_readBytes(JNIEnv *env, jclass, jstring path) {
    try {
        auto bytes = readFileBytes(env, requirePath(env, path, "path"));
        jbyteArray array = env->NewByteArray(static_cast<jsize>(bytes.size()));
        if (array == nullptr) {
            throwIOException(env, "Failed to allocate byte array");
        }
        if (!bytes.empty()) {
            env->SetByteArrayRegion(
                array, 0, static_cast<jsize>(bytes.size()),
                reinterpret_cast<const jbyte *>(bytes.data())
            );
        }
        return array;
    } catch (...) {
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_lumkit_tweak_sharednative_NativeFileBridge_writeBytes(JNIEnv *env, jclass, jstring path, jbyteArray bytes) {
    try {
        if (bytes == nullptr) {
            throwIOException(env, "bytes must not be null");
        }
        std::string resolvedPath = requirePath(env, path, "path");
        jsize size = env->GetArrayLength(bytes);
        std::vector<unsigned char> buffer(static_cast<size_t>(size));
        if (size > 0) {
            env->GetByteArrayRegion(bytes, 0, size, reinterpret_cast<jbyte *>(buffer.data()));
        }
        writeFileBytes(env, resolvedPath, buffer.data(), buffer.size());
    } catch (...) {
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_lumkit_tweak_sharednative_NativeFileBridge_delete(JNIEnv *env, jclass, jstring path, jboolean recursive) {
    try {
        deleteRecursively(env, requirePath(env, path, "path"), recursive == JNI_TRUE);
    } catch (...) {
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_lumkit_tweak_sharednative_NativeFileBridge_list(JNIEnv *env, jclass, jstring path) {
    try {
        auto entries = listDirectory(env, requirePath(env, path, "path"));
        jclass stringClass = env->FindClass("java/lang/String");
        jobjectArray array = env->NewObjectArray(static_cast<jsize>(entries.size()), stringClass, nullptr);
        if (array == nullptr) {
            throwIOException(env, "Failed to allocate string array");
        }
        for (jsize index = 0; index < static_cast<jsize>(entries.size()); index++) {
            jstring value = env->NewStringUTF(entries[static_cast<size_t>(index)].c_str());
            env->SetObjectArrayElement(array, index, value);
            env->DeleteLocalRef(value);
        }
        return array;
    } catch (...) {
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_lumkit_tweak_sharednative_NativeFileBridge_mkdirs(JNIEnv *env, jclass, jstring path) {
    try {
        std::string resolvedPath = requirePath(env, path, "path");
        if (resolvedPath.empty()) {
            throwIOException(env, "path must not be empty");
        }

        std::string current;
        if (resolvedPath.front() == '/') {
            current = "/";
        }
        std::stringstream stream(resolvedPath);
        std::string part;
        while (std::getline(stream, part, '/')) {
            if (part.empty()) {
                continue;
            }
            current = joinPath(current, part);
            if (mkdir(current.c_str(), 0777) != 0 && errno != EEXIST) {
                throwIOException(env, lastError("Failed to create directory " + current));
            }
        }
    } catch (...) {
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_lumkit_tweak_sharednative_NativeFileBridge_copy(JNIEnv *env, jclass, jstring sourcePath, jstring targetPath, jboolean overwrite) {
    try {
        copyRecursively(env, requirePath(env, sourcePath, "sourcePath"), requirePath(env, targetPath, "targetPath"), overwrite == JNI_TRUE);
    } catch (...) {
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_lumkit_tweak_sharednative_NativeFileBridge_move(JNIEnv *env, jclass, jstring sourcePath, jstring targetPath, jboolean overwrite) {
    try {
        std::string source = requirePath(env, sourcePath, "sourcePath");
        std::string target = requirePath(env, targetPath, "targetPath");
        bool shouldOverwrite = overwrite == JNI_TRUE;
        ensureParentDirectory(env, target);
        if (shouldOverwrite && pathExists(target)) {
            deleteRecursively(env, target, true);
        } else if (!shouldOverwrite && pathExists(target)) {
            errno = EEXIST;
            throwIOException(env, lastError("Target already exists " + target));
        }

        if (rename(source.c_str(), target.c_str()) != 0) {
            if (errno != EXDEV) {
                throwIOException(env, lastError("Failed to move " + source));
            }
            copyRecursively(env, source, target, shouldOverwrite);
            deleteRecursively(env, source, true);
        }
    } catch (...) {
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_lumkit_tweak_sharednative_NativeFileBridge_chmod(JNIEnv *env, jclass, jstring path, jstring mode) {
    try {
        std::string resolvedPath = requirePath(env, path, "path");
        mode_t resolvedMode = parseMode(env, requirePath(env, mode, "mode"));
        if (chmod(resolvedPath.c_str(), resolvedMode) != 0) {
            throwIOException(env, lastError("Failed to chmod " + resolvedPath));
        }
    } catch (...) {
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_lumkit_tweak_sharednative_NativeFileBridge_length(JNIEnv *env, jclass, jstring path) {
    try {
        return calculateLength(env, requirePath(env, path, "path"));
    } catch (...) {
        return -1;
    }
}
