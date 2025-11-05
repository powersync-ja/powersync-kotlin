cmake_minimum_required(VERSION 3.14)
project(
    powersync_sqlite3mc_bundled
    VERSION 3
    LANGUAGES C CXX
)

set(CMAKE_C_FLAGS "-O3")

add_library(sqlite3mc_bundled SHARED "../../build/downloads/sqlite3mc/sqlite3mc_amalgamation.c" "sqlite_bindings.cpp")
target_include_directories(sqlite3mc_bundled PRIVATE "../../build/downloads/sqlite3mc")

# Note: Keep in sync with the ClangCompile task used for static-sqlite-driver
target_compile_definitions(sqlite3mc_bundled PUBLIC
    HAVE_GETHOSTUUID=0
    SQLITE_ENABLE_DBSTAT_VTAB
    SQLITE_ENABLE_FTS5
    SQLITE_ENABLE_RTREE
    SQLITE_ENABLE_SNAPSHOT
    SQLITE_ENABLE_SESSION
    SQLITE_ENABLE_PREUPDATE_HOOK
)
