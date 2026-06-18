#include <jni.h>

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void * /*reserved*/) {
    // TODO: 在此添加 Native 签名验证逻辑，防止 so 被其他应用加载
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
JNI_OnUnload(JavaVM * /*vm*/, void * /*reserved*/) {

}
