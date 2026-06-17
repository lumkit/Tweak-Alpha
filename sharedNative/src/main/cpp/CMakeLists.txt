cmake_minimum_required(VERSION 3.22.1)

project("tweak_shared_native")

add_library(
        ${CMAKE_PROJECT_NAME}
        SHARED
        native-lib.cpp
)

target_link_libraries(
        ${CMAKE_PROJECT_NAME}
        android
        EGL
        GLESv2
        log
)
