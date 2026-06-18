#include "native_common.h"
#include <sys/system_properties.h>

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_lumkit_tweak_sharednative_SystemPropertyBridge_get(JNIEnv *env, jclass, jstring name) {
    try {
        std::string propertyName = requirePath(env, name, "name");
        std::vector<char> buffer(static_cast<size_t>(PROP_VALUE_MAX) + 1, '\0');
        int length = __system_property_get(propertyName.c_str(), buffer.data());
        if (length < 0) {
            throwIOException(env, "Failed to read system property " + propertyName);
        }
        buffer[static_cast<size_t>(length)] = '\0';
        return env->NewStringUTF(buffer.data());
    } catch (...) {
        return env->NewStringUTF("");
    }
}
