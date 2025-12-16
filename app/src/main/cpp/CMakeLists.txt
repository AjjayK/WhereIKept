cmake_minimum_required(VERSION 3.22.1)

project("whisper_android")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Add whisper.cpp source files
add_subdirectory(whisper.cpp)

# Build the JNI wrapper
add_library(${CMAKE_PROJECT_NAME} SHARED
        whisper_jni.cpp)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        whisper.cpp
        whisper.cpp/include)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log
        whisper)
