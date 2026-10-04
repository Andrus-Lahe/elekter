#!/usr/bin/env bash
# Builds elekter, copies it to the Raspberry Pi and (re)starts the systemd service.
#
# Usage: deploy/deploy.sh [--setup] [user@host]
#   --setup   first time only: install Java (OpenJDK 21, else 17) and gpiod and set the time zone to Europe/Tallinn
#   host      default: $PI_HOST, else pi@elekter.local
set -euo pipefail

SETUP=0
if [[ "${1:-}" == "--setup" ]]; then SETUP=1; shift; fi
HOST="${1:-${PI_HOST:-pi@elekter.local}}"
[[ "$HOST" == *@* ]] || HOST="pi@$HOST"
PI_USER="${HOST%%@*}"

cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" && -d "$HOME/.jdks/openjdk-26.0.1" ]]; then
    export JAVA_HOME="$HOME/.jdks/openjdk-26.0.1"
fi

echo "==> Building (tests + jar)"
./gradlew test bootJar
JAR=$(ls build/libs/elekter-*.jar | grep -v -- '-plain' | head -n 1)

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
sed "s/^User=.*/User=$PI_USER/" deploy/elekter.service > "$TMP/elekter.service"

if [[ $SETUP -eq 1 ]]; then
    echo "==> First-time setup on $HOST"
    # openjdk-21 where the OS ships it, otherwise 17 (the jar targets Java 17 and runs on both)
    ssh -t "$HOST" "sudo apt-get update && (sudo apt-get install -y openjdk-21-jre-headless gpiod || sudo apt-get install -y openjdk-17-jre-headless gpiod) && sudo timedatectl set-timezone Europe/Tallinn"
fi

echo "==> Copying to $HOST:/opt/elekter"
ssh -t "$HOST" "sudo mkdir -p /opt/elekter && sudo chown $PI_USER:$PI_USER /opt/elekter"
scp "$JAR" "$HOST:/opt/elekter/elekter.jar.new"
scp "$TMP/elekter.service" "$HOST:/opt/elekter/elekter.service.new"

echo "==> Installing and restarting the service"
# mv within /opt/elekter is atomic, so the running JVM keeps its old jar until the restart
ssh -t "$HOST" "set -e; cd /opt/elekter; mv -f elekter.jar.new elekter.jar;
  if ! cmp -s elekter.service.new /etc/systemd/system/elekter.service; then
    sudo install -m 644 elekter.service.new /etc/systemd/system/elekter.service; sudo systemctl daemon-reload;
  fi;
  rm -f elekter.service.new; sudo systemctl enable elekter; sudo systemctl restart elekter"

echo "==> Waiting for the app to answer (the first start on a Pi 3 can take about a minute)"
if ssh "$HOST" 'for i in $(seq 60); do curl -sf localhost:8080/api/status && exit 0; sleep 2; done; exit 1'; then
    echo
    echo "==> Done: http://${HOST#*@}:8080/"
else
    echo "==> No answer from the app. Last log lines:" >&2
    ssh "$HOST" 'journalctl -u elekter -n 30 --no-pager' >&2 || true
    exit 1
fi
