cmake_minimum_required(VERSION 3.22.1)

project("t3dof_native")

add_library(
        t3dof_native
        SHARED
        native-lib.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        t3dof_native
        ${log-lib})
