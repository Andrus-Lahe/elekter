# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

`elekter` is a small Gradle + Spring Boot (4.1.0, `spring-boot-starter-web`) app. It serves the current and future 15-minute day-ahead electricity prices for the Estonian (EE) area, fetched from the Nord Pool dataportal API. There is no test suite yet.

## Code structure

- `src/main/java/elekter/ElekterApplication.java` — Spring Boot entry point.
- `src/main/java/elekter/PriceController.java` — `GET /prices` returns plain text, one line per interval (`start - end price`, EUR/MWh). It fetches today's and tomorrow's (UTC) prices, extracts them with a regex rather than a JSON parser, and drops intervals that have already ended. Tomorrow's prices only appear once Nord Pool has published them.
- `build.gradle` / `settings.gradle` / `gradlew` — Gradle 9.8 build with the Gradle wrapper; Java toolchain 26.
- `.claude/skills/` — skills copied from the `toolrental` project; git-ignored, not part of this app.

## Build / run

The JDK on PATH may be an older one (JDK 17 here), which Gradle can't use for this project. Point `JAVA_HOME` at JDK 26 first (IntelliJ's copy is at `~/.jdks/openjdk-26.0.1`):

```
export JAVA_HOME=~/.jdks/openjdk-26.0.1
./gradlew bootRun     # serves http://localhost:8080/prices
./gradlew build       # compile + bootJar (build/libs/elekter-0.0.1.jar)
```

Gradle and the Spring Boot CLI were installed via SDKMAN (`source ~/.sdkman/bin/sdkman-init.sh`), but the project itself only needs `./gradlew`.
