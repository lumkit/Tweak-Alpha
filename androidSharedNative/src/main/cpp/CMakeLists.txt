cmake_minimum_required(VERSION 3.22.1)

project("tweak_shared_native")

add_library(
        ${CMAKE_PROJECT_NAME}
        SHARED
        native_main.cpp
        native_file.cpp
        native_gpu.cpp
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
endif ()
