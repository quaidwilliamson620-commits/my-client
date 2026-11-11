# PvpClient (phase 1: framework + config)

Target: Minecraft 26.2, Fabric Loader 0.19.x, Fabric API 0.161.0+26.2, Java 25, Mojang mappings.

## Build
1. Install JDK 25.
2. From this folder run `gradle wrapper --gradle-version 9.5.1` once (needs a local Gradle), or import into your IDE.
3. `./gradlew build` -> `build/libs/pvpclient-0.1.0.jar`.
4. Check the versions in `gradle.properties` against https://fabricmc.net/develop and adjust if they differ.

## Install
Copy the jar (not `-sources`) into `.minecraft/mods/` with Fabric Loader and Fabric API for 26.2.

## Config
`.minecraft/config/pvpclient/profiles/<name>.json`, active profile in `state.json`.
Corrupt files are renamed `*.corrupt` and defaults are used.

## Status
Phase 1 only: module base class, registry, event bus, typed settings, JSON profiles, test-environment guard.
No GUI, HUD, keybind handling or real modules yet.
