# Minimum CMake version required
cmake_minimum_required(VERSION 3.10)

# Project name and version
project(HttpServer VERSION 1.0 LANGUAGES CXX)

# Specify C++ standard
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED True)

# Define compiler flags
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -D_GLIBCXX_USE_CXX11_ABI=1 -pthread")

# Find OpenTelemetry package
find_package(opentelemetry-cpp CONFIG REQUIRED)

# Include directories
include_directories(${Boost_INCLUDE_DIRS})                    

# Link directories
link_directories(${Boost_LIBRARY_DIRS})                       

# Find required packages (Boost, CURL, and Protobuf)
find_package(Boost REQUIRED COMPONENTS system thread)          
find_package(CURL REQUIRED)                                    
find_package(Protobuf REQUIRED) 
find_package(nlohmann_json REQUIRED)                        

# Display the OpenTelemetry libraries found
message("OpenTelemetry libraries found: ${OpenTelemetry_LIBRARIES}")

# Define the executable target
add_executable(http_server main.cpp)

# # Link the libraries
# target_link_libraries(http_server
#     ${Boost_LIBRARIES}  
#     ${CURL_LIBRARIES}
#     ${Protobuf_LIBRARIES}
#     pthread                                 
# )

target_include_directories(http_server PRIVATE ${OPENTELEMETRY_CPP_INCLUDE_DIRS})
target_link_libraries(http_server PRIVATE ${OPENTELEMETRY_CPP_LIBRARIES} ${Boost_LIBRARIES} ${CURL_LIBRARIES} ${Protobuf_LIBRARIES} nlohmann_json::nlohmann_json pthread)
