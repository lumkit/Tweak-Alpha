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

# starter 必须是可执行 ELF（有 main 入口）。AGP Release 不会自动打包 executable，
# 因此 POST_BUILD 拷贝为 libtweak_starter.so 到 CMAKE_LIBRARY_OUTPUT_DIRECTORY，随 jni 打包进 APK。
add_executable(
        tweak_starter_bin
        tweak_starter/starter.cpp
)

target_link_libraries(
        tweak_starter_bin
        log
)

if (DEFINED CMAKE_LIBRARY_OUTPUT_DIRECTORY)
    add_custom_command(
            TARGET tweak_starter_bin
            POST_BUILD
            COMMAND ${CMAKE_COMMAND} -E make_directory "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
            COMMAND ${CMAKE_COMMAND} -E copy
            "$<TARGET_FILE:tweak_starter_bin>"
            "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}/libtweak_starter.so"
            COMMENT "Package tweak_starter_bin as libtweak_starter.so for APK jniLibs"
    )
    # 确保先编 starter 再打包 jni（依赖共享库构建链路时也会带上）
    add_dependencies(${CMAKE_PROJECT_NAME} tweak_starter_bin)
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
    target_link_options(tweak_starter_bin PRIVATE
            -Wl,--gc-sections
            -Wl,--strip-all
    )
endif ()
