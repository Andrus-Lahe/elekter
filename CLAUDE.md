# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

`elekter` is a small Gradle + Spring Boot (4.1.0, `spring-boot-starter-web`) app. It stores the 15-minute day-ahead electricity prices for the Estonian (EE) area, fetched from the Nord Pool dataportal API, and switches a relay on GPIO17 by comparing the current price with a limit. Tests are in `src/test/java/elekter` (`./gradlew test`).

## Code structure

- `src/main/java/elekter/ElekterApplication.java` — Spring Boot entry point.
- `src/main/java/elekter/PriceService.java` — price storage: one text file per Nord Pool delivery date in `prices/` (e.g. `2026-10-03.txt`, lines `start;end;price` in EET; configurable with `elekter.prices.dir`). Delivery days follow CET, so a file runs from 01:00 EET of its date to 01:00 EET of the next day. Also does the HTTP download from Nord Pool (regex parsing, no JSON parser).
- `src/main/java/elekter/PriceDownloader.java` — scheduling: downloads tomorrow's prices daily at 14:30 EET (3 retries, 2 minutes apart), a catch-up job at startup and every 15 minutes (today's file if no price covers now, tomorrow's if past 14:30 and missing), and an hourly :05 cleanup that deletes files whose last interval has ended (in practice 01:05 EET).
- `src/main/java/elekter/PriceController.java` — `GET /prices` returns the saved current and future prices as plain text (`start - end price`, EET, EUR/MWh).
- `src/main/java/elekter/NtpClock.java` — SNTP client (plain UDP, no dependency) that checks the system clock against `pool.ntp.org` at startup, then hourly (every 2 minutes while the clock is wrong or the server is unreachable). It only measures; the OS (systemd-timesyncd) corrects the clock. If the offset is above 60 s, `isWrong()` is true: the switch goes OFF (`/api/status` returns no price) and downloads are skipped. An unreachable NTP server does not block the switch. Options: `elekter.ntp.server`, `elekter.ntp.port`, `elekter.ntp.threshold-seconds`.
- `src/main/java/elekter/StatusController.java` — REST API: `GET /api/status` (current price, limit, `switchOn`, interval start, plus `ntp`, `checkedAt`, `relay`, `prices` status objects) and `PUT /api/limit` (body `{"limit": 150}`, accepted range -10..1000, otherwise 400 with Spring's default error body, which has no message text).
- `src/main/java/elekter/SwitchService.java` — holds the price limit (`limit.txt` in the working directory, default 100) and decides the switch state: ON while the current price is `<=` the limit, OFF otherwise or when there is no price info. `@Scheduled` re-applies it every 3 minutes and `PUT /api/limit` applies it immediately. `GET /api/status` also reports the last check time, the relay state (`Relay.Info`) and the file status (`PriceService.FileStatus`), which the page shows in its bottom status block.
- `src/main/java/elekter/Relay.java` — drives the relay on GPIO17 (BCM) via the libgpiod `gpioset` command (v1 and v2 syntax auto-detected). `info()` reports whether the gpioset process holding the line is alive (libgpiod cannot read a line that another process holds, so the pin level itself is not read back); a dead process is restarted on the next check (every 3 minutes). Does nothing unless `--elekter.gpio.enabled=true`; options `--elekter.gpio.active-low=true`, `--elekter.gpio.chip=gpiochip0` and `--elekter.gpio.command=gpioset` (path of the gpioset binary, used by the tests with a fake script).
- `src/main/resources/static/index.html` — the single-page UI (plain HTML/CSS/JS, no Vue or build step): current price, ON/OFF badge that follows the slider live, a row of 40 boxes on a compressed scale (-10..1000; stops in the `STOPS` array) with a red price marker, the limit slider (saved on release) and a status block (switch/relay, price files, NTP).
- `src/main/resources/application.properties` — scheduler pool size (3, so a slow download or NTP query cannot block the 3-minute switch check).
- `build.gradle` / `settings.gradle` / `gradlew` — Gradle 9.8 build with the Gradle wrapper; compiled with `--release 17` so the jar runs on JDK 17+.
- `deploy/elekter.service` — systemd unit for the Raspberry Pi (see below).
- `deploy/deploy.sh` — builds (tests + bootJar), copies jar and unit to the Pi over ssh/scp, restarts the service and waits for `/api/status`; `--setup` also installs Java 17 and gpiod. Usage: `deploy/deploy.sh [--setup] [user@host]` (default `$PI_HOST` or `pi@elekter.local`).
- `.claude/skills/` — skills copied from the `toolrental` project; git-ignored, not part of this app.

## Build / run

Any JDK 17+ with `javac` works (the build and tests were run with JDK 17 and with JDK 26; IntelliJ's JDK 26 is at `~/.jdks/openjdk-26.0.1`). If `JAVA_HOME` points to a JRE without `javac`, set it to a full JDK first.

```
./gradlew bootRun     # page at http://localhost:8080/, prices at /prices, API at /api/status
./gradlew test        # unit tests
./gradlew build       # compile + tests + bootJar (build/libs/elekter-0.0.1.jar)
```

The app writes `limit.txt` and `prices/` into the working directory (both git-ignored).

Gradle and the Spring Boot CLI were installed via SDKMAN (`source ~/.sdkman/bin/sdkman-init.sh`), but the project itself only needs `./gradlew`.

## Raspberry Pi 3 Model B deployment

Full step-by-step guide (Estonian): `docs/raspberry-pi-paigaldus.md`. The quickest way is `deploy/deploy.sh`; the manual commands below do the same.

Target: Raspberry Pi OS 64-bit (Bookworm), `sudo apt install openjdk-17-jre-headless gpiod`, timezone `Europe/Tallinn`, NTP via systemd-timesyncd.

```
./gradlew bootJar
scp build/libs/elekter-0.0.1.jar pi@<pi-ip>:/opt/elekter/elekter.jar
scp deploy/elekter.service pi@<pi-ip>:/tmp/ && ssh pi@<pi-ip> 'sudo mv /tmp/elekter.service /etc/systemd/system/ && sudo systemctl daemon-reload && sudo systemctl enable --now elekter'
```

The service runs in `/opt/elekter` (create it and `chown` it to `pi` first); `limit.txt` is written there. The service user must be in the `gpio` group (default for `pi`). The unit starts the app with `--elekter.gpio.enabled=true`; on other machines GPIO stays off and the relay state is only logged. The relay is an active-high SSR (LCDK-480DA, see `docs/raspberry-pi-rele.md`: GPIO17 pin 11 -> terminal 3 +, GND pin 9 -> terminal 4 -); when the app stops the GPIO line is released and falls LOW, so the relay turns OFF (fail-safe). An active-low board would need `--elekter.gpio.active-low=true` and would turn ON on release. The page has no authentication — keep port 8080 inside the LAN.
