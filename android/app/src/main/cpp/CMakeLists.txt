cmake_minimum_required(VERSION 3.31.6)
project(offia_memory LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(OFFIA_ANDROID_ROOT ${CMAKE_CURRENT_LIST_DIR}/../../../..)
set(MEMORIA_SOURCE_DIR ${OFFIA_ANDROID_ROOT}/vendor/memoria.ia)
set(BDR_SOURCE_DIR ${OFFIA_ANDROID_ROOT}/vendor/resolutive-DB CACHE PATH "Resolutive-DB source" FORCE)
set(MEMORIA_BDR_ROOT ${BDR_SOURCE_DIR} CACHE PATH "Pinned Resolutive-DB checkout used by Memoria.ia" FORCE)

if(NOT EXISTS "${MEMORIA_SOURCE_DIR}/native/mobile/CMakeLists.txt")
    message(FATAL_ERROR "Pinned Memoria.ia native mobile runtime not found: ${MEMORIA_SOURCE_DIR}")
endif()
if(NOT EXISTS "${MEMORIA_SOURCE_DIR}/include/memoria_mobile.h")
    message(FATAL_ERROR "Pinned Memoria.ia mobile ABI header not found: ${MEMORIA_SOURCE_DIR}/include/memoria_mobile.h")
endif()
if(NOT EXISTS "${BDR_SOURCE_DIR}/experimental/api_v86/include/bdr/atomic_c_api.h")
    message(FATAL_ERROR "Pinned BDR atomic C ABI source not found: ${BDR_SOURCE_DIR}")
endif()

add_subdirectory(${MEMORIA_SOURCE_DIR}/native/mobile ${CMAKE_BINARY_DIR}/memoria-mobile-build)

add_library(offia-memory SHARED offia_memory_jni.cpp)
target_include_directories(offia-memory PRIVATE ${MEMORIA_SOURCE_DIR}/include)
target_link_libraries(offia-memory PRIVATE memoria_mobile android log)
