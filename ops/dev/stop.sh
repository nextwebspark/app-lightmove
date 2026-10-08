#!/usr/bin/env bash
# Frees the ports `npm run dev` listens on: the API's 8080 and Vite's 5173. Pass ports to free others.
#
# Why this exists: in Git Bash's own window (mintty) Ctrl+C never reaches the API. mintty is not a Windows
# console, so node and java get no console Ctrl+C event — the chain is killed from the top instead — and
# Windows does not end a process's children with it. `spring-boot:run` runs the app in a JVM of its own under
# Maven's, so that JVM is left holding 8080 and the next `npm run dev` fails with "Port 8080 was already in
# use". Windows Terminal, PowerShell and cmd deliver Ctrl+C properly; this is for when it was not.
#
# Leaves the Docker Postgres running: `npm run dev:db:down` stops that.
set -euo pipefail

PORTS=("$@")
if [ ${#PORTS[@]} -eq 0 ]; then
  PORTS=(8080 5173)
fi

listeners_of() {
  local port="$1"
  case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*)
      netstat -ano -p TCP | awk -v port="$port" '$4 == "LISTENING" && $2 ~ ":" port "$" { print $5 }'
      netstat -ano -p TCPv6 | awk -v port="$port" '$4 == "LISTENING" && $2 ~ ":" port "$" { print $5 }'
      ;;
    *)
      lsof -t -iTCP:"$port" -sTCP:LISTEN 2>/dev/null || true
      ;;
  esac
}

stop_process() {
  local pid="$1"
  case "$(uname -s)" in
    # //T takes the process's own children with it; //F because a JVM orphaned this way ignores a polite ask.
    MINGW* | MSYS* | CYGWIN*) taskkill //PID "$pid" //T //F > /dev/null ;;
    *) kill "$pid" ;;
  esac
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
