# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

This is a minimal, single-file Java project generated from IntelliJ IDEA's default "New Project" onboarding template. There is no build tool (no Maven/Gradle), no dependencies, and no test suite — just `src/Main.java`.

The project is not (yet) a git repository.

## Code structure

- `src/Main.java` — the only source file. It uses Java's implicit-class / instance-`main` syntax (no `class` wrapper, no `public static`), a feature finalized in later JDKs (JEP 512). The `.idea/misc.xml` project SDK is set to JDK 26.

## Build / run

There is no build script. Compile and run directly with `javac`/`java` from a JDK new enough to support unnamed classes and instance main methods (JDK 25+; earlier JDKs need `--enable-preview`), or run via IntelliJ IDEA's gutter "Run" action.

Note: the system's default `java`/`javac` may be an older LTS version (this environment currently only has a JDK 17 `java` runtime on PATH, and no `javac`) — check `java -version` and locate a matching JDK (e.g. via `update-alternatives --list java` or IntelliJ's configured project SDK) before assuming compilation will work with whatever is on PATH.
