# Repository Guidelines

## Project Structure & Module Organization

Stash is a single-module Android app. Gradle configuration lives in `settings.gradle.kts`, the root `build.gradle.kts`, and `gradle/libs.versions.toml`; application configuration is in `app/build.gradle.kts`. Production Kotlin code is under `app/src/main/java/dev/cburlacu/stash/`, organized cleanly into `ai`, `data/extract`, `data/image`, `data/prompt`, `data/local`, `models`, `ui/adaptive`, `ui/briefing`, `ui/chat`, `ui/components`, `ui/detail`, `ui/feed`, `ui/settings`, and `ui/theme`. Android resources and the manifest are under `app/src/main/res/` and `app/src/main/`. Host-side tests belong in `app/src/test/`; device/emulator tests belong in `app/src/androidTest/`. `examples/` contains reference HTML and screenshot assets.

## Build, Test, and Development Commands

Use the Gradle wrapper from the repository root:

```text
./gradlew assembleDebug       # Build the debug APK
./gradlew installDebug        # Build and install on a connected device/emulator
./gradlew test                # Run local JVM unit tests
./gradlew connectedAndroidTest # Run instrumentation tests on a device/emulator
```

On Windows, use `gradlew.bat` with the same tasks. Android Studio can also run the `app` configuration directly. The project targets SDK 37 and requires Android 14/API 34 or newer at runtime.

## Coding Style & Naming Conventions

Write Kotlin with four-space indentation, standard Kotlin formatting, and trailing commas where they improve multiline readability. Use `PascalCase` for classes and composables (for example, `StashAdaptiveLayout`), `camelCase` for functions, properties, and parameters, and descriptive `UPPER_SNAKE_CASE` only for constants. Keep UI code in composable functions and reuse `MaterialTheme` tokens; do not introduce hardcoded colors or spacing when an existing theme token or design-system value applies. No formatter or linter is configured, so keep imports clean and review formatting before committing.

## Testing Guidelines

Name test classes after the subject under test and test methods as behavior descriptions, such as `searchFiltersMatchingItems`. Put pure logic tests in `app/src/test`; use `app/src/androidTest` for Compose, framework, or device behavior. Run both `test` and `connectedAndroidTest` when changing application behavior. Add regression coverage for bug fixes; no explicit coverage percentage is currently enforced.

## Commit & Pull Request Guidelines

Git history is not available in this checkout, so follow concise imperative commit subjects, preferably scoped (for example, `ui: refine adaptive feed layout`). Keep commits focused. Pull requests should explain the behavior change, list validation commands, link the relevant issue when one exists, and include emulator screenshots or a short recording for visual/UI changes. Call out target-SDK, dependency, or configuration changes explicitly.

## Security & Configuration Tips

Do not commit `local.properties`, signing credentials, API keys, or generated `build/` output. Keep privacy-sensitive processing on-device and review manifest, backup, and data-extraction changes carefully.
