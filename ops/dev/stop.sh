#!/usr/bin/env bash
# Frees the ports `npm run dev` listens on: the API's 8080 and Vite's 5173. Pass ports to free others.
#
# Why this exists: in Git Bash's own window (mintty) Ctrl+C never reaches the API. mintty is not a Windows
# console, so node and java get no console Ctrl+C event — the chain is killed from the top instead — and
# Windows does not end a process's children with it. `spring-boot:run` runs the app in a JVM of its own under
# Maven's, so that JVM is left holding 8080 and the next `npm run dev` fails with "Port 8080 was already in
# use". Windows Terminal, PowerShell and cmd deliver Ctrl+C properly; this is for when it was not.
#
# It stops whatever listens on a port, without asking whose it is: right for the defaults, and worth a look
# before passing a port something else of yours may hold. Leaves the Docker Postgres to `dev:db:down`.
set -euo pipefail

PORTS=("$@")
if [ ${#PORTS[@]} -eq 0 ]; then
  PORTS=(8080 5173)
fi

# A port reaches awk as part of a regex and lsof as part of a filter, so "80*" or "8080." would match others.
for port in "${PORTS[@]}"; do
  if ! [[ $port =~ ^[0-9]+$ ]] || [ "$port" -lt 1 ] || [ "$port" -gt 65535 ]; then
    echo "Invalid port: $port (expected a number from 1 to 65535)" >&2
    exit 2
  fi
done

case "$(uname -s)" in
  MINGW* | MSYS* | CYGWIN*) IS_WINDOWS=true ;;
  *) IS_WINDOWS=false ;;
esac

# Without lsof every port would read as free while still held.
if [ "$IS_WINDOWS" = false ] && ! command -v lsof > /dev/null; then
  echo "lsof is not installed, so the ports cannot be checked. Install it (e.g. apt install lsof) and retry." >&2
  exit 1
fi

listeners_of() {
  local port="$1"
  if [ "$IS_WINDOWS" = true ]; then
    netstat -ano -p TCP | awk -v port="$port" '$4 == "LISTENING" && $2 ~ ":" port "$" { print $5 }'
    netstat -ano -p TCPv6 | awk -v port="$port" '$4 == "LISTENING" && $2 ~ ":" port "$" { print $5 }'
  else
    # lsof exits 1 when nothing listens; that is an answer, not a failure.
    lsof -t -iTCP:"$port" -sTCP:LISTEN || true
  fi
}

stop_process() {
  local pid="$1"
  if [ "$IS_WINDOWS" = true ]; then
    # //T takes the process's own children with it; //F because a JVM orphaned this way ignores a polite ask.
    taskkill //PID "$pid" //T //F > /dev/null
  else
    kill "$pid"
  fi
}

for port in "${PORTS[@]}"; do
  pids=$(listeners_of "$port" | sort -u)
  if [ -z "$pids" ]; then
    echo "Port $port is free."
    continue
  fi
  for pid in $pids; do
    if stop_process "$pid"; then
      echo "Port $port: stopped process $pid."
    else
      echo "Port $port: could not stop process $pid." >&2
    fi
  done
done
