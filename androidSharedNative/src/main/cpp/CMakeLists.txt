cmake_minimum_required(VERSION 3.22.1)

project("tweak_shared_native")

add_library(
        ${CMAKE_PROJECT_NAME}
        SHARED
        native_main.cpp
        native_file.cpp
        native_gpu.cpp
        native_cycles.cpp
        native_sysprop.cpp
        native_storage.cpp
)

target_link_libraries(
        ${CMAKE_PROJECT_NAME}
        android
        EGL
        GLESv2
        log
)

# 特权 native daemon（独立 ELF，非 JNI）
add_executable(
        tweakd
        tweakd/tweakd_main.cpp
)

target_link_libraries(
        tweakd
        log
)

# 打包进 jniLibs：以 libtweakd.so 名义打进 APK，运行时再拷到 /data/local/tmp 并 chmod +x
if (DEFINED ANDROID_ABI)
    set(TWEAKD_JNI_LIBS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")
    add_custom_command(
            TARGET tweakd
            POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E make_directory "${TWEAKD_JNI_LIBS_DIR}"
            COMMAND ${CMAKE_COMMAND} -E copy
            "$<TARGET_FILE:tweakd>"
            "${TWEAKD_JNI_LIBS_DIR}/libtweakd.so"
            COMMENT "Copy tweakd -> jniLibs/${ANDROID_ABI}/libtweakd.so"
    )
endif ()

# Release 构建保护：strip 符号 + gc-sections 消除无用代码 + LTO
if (CMAKE_BUILD_TYPE STREQUAL "Release")
    target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
            -Wl,--gc-sections
            -Wl,--strip-all
            -flto
    )
    # 只导出 JNI 函数，隐藏其余所有符号
    target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
            -Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/exported_symbols.map
    )
    target_link_options(tweakd PRIVATE
            -Wl,--gc-sections
            -Wl,--strip-all
            -flto
    )
endif ()
