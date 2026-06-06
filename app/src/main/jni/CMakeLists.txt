cmake_minimum_required(VERSION 3.22.1)
project("shell-engine")

add_library(
    shell-engine
    SHARED
    shell.c
)

find_library(log-lib log)

target_link_libraries(
    shell-engine
    ${log-lib}
)
