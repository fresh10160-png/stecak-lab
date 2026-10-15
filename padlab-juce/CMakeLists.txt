# Desktop development build (Linux/macOS/Windows) + core tests.
# The Android APK is produced from PadLab.jucer by Projucer in CI (see ../.github/workflows).
cmake_minimum_required(VERSION 3.22)
project(PadLab VERSION 2.0.0 LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(JUCE_DIR "" CACHE PATH "Path to a JUCE checkout (8.x)")
if(NOT JUCE_DIR)
    message(FATAL_ERROR "Pass -DJUCE_DIR=/path/to/JUCE")
endif()
add_subdirectory(${JUCE_DIR} juce EXCLUDE_FROM_ALL)

file(GLOB CORE_SRC CONFIGURE_DEPENDS Source/core/*.cpp)
file(GLOB UI_SRC CONFIGURE_DEPENDS Source/ui/*.cpp)

juce_add_gui_app(PadLab
    PRODUCT_NAME "PadLab"
    COMPANY_NAME "StecakLab")
target_sources(PadLab PRIVATE Source/Main.cpp Source/MainComponent.cpp Source/AudioHost.cpp ${UI_SRC} ${CORE_SRC})
target_include_directories(PadLab PRIVATE Source)
target_compile_definitions(PadLab PRIVATE
    JUCE_WEB_BROWSER=0 JUCE_USE_CURL=0 JUCE_APPLICATION_NAME_STRING="PadLab" JUCE_APPLICATION_VERSION_STRING="2.0.0"
    JUCE_USE_MP3AUDIOFORMAT=1 JUCE_DISPLAY_SPLASH_SCREEN=0)
target_link_libraries(PadLab PRIVATE
    juce::juce_audio_basics juce::juce_audio_devices juce::juce_audio_formats juce::juce_audio_utils
    juce::juce_core juce::juce_events juce::juce_graphics juce::juce_gui_basics juce::juce_gui_extra
    juce::juce_recommended_config_flags juce::juce_recommended_warning_flags)

# Pure-C++ core tests (no JUCE).
add_executable(CoreTests tests/CoreTests.cpp ${CORE_SRC})
target_include_directories(CoreTests PRIVATE Source)
find_package(Threads REQUIRED)
target_link_libraries(CoreTests PRIVATE Threads::Threads)
