cmake_minimum_required(VERSION 3.18.1)

project(powersync-sqlite)

set(PACKAGE_NAME "powersync-sqlite")
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/../output)

find_package(JNI REQUIRED)

include_directories(${JNI_INCLUDE_DIRS})
include_directories(${SQLITE3_INTEROP_DIR})

add_library(sqlite3_static STATIC
        "${SQLITE3_INTEROP_DIR}/sqlite3.c"
)

add_library(
        ${PACKAGE_NAME}
        SHARED
        sqlite_bindings.cpp
)

target_link_libraries(
        ${PACKAGE_NAME}
        PRIVATE
        sqlite3_static
)