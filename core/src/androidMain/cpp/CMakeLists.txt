cmake_minimum_required(VERSION 3.18.1)

project(powersync-sqlite)

set(PACKAGE_NAME "powersync-sqlite")
set(BUILD_DIR ${CMAKE_SOURCE_DIR}/build)

add_library(
        ${PACKAGE_NAME}
        SHARED
        sqlite_bindings.cpp
        "${SQLITE3_SRC_DIR}/sqlite3.c"
)

target_include_directories(
        ${PACKAGE_NAME}
        PRIVATE
        "${SQLITE3_SRC_DIR}"
)

target_link_libraries(
        ${PACKAGE_NAME}
        log
        android
)