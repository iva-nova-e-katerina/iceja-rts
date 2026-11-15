#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_DIR="${ROOT_DIR}/.run"
LOG_DIR="${RUN_DIR}/logs"
SERVER_PORT="${SERVER_PORT:-8080}"
CLIENT_PORT="${CLIENT_PORT:-5173}"
SERVER_JAR="${ROOT_DIR}/server/target/iceja-rts-server.jar"

mkdir -p "${LOG_DIR}"

if ! command -v mvn >/dev/null 2>&1; then
  echo "mvn is required" >&2
  exit 1
fi
if ! command -v npm >/dev/null 2>&1; then
  echo "npm is required" >&2
  exit 1
fi

if [[ -f "${RUN_DIR}/server.pid" ]] && kill -0 "$(cat "${RUN_DIR}/server.pid")" 2>/dev/null; then
  echo "server already running (pid $(cat "${RUN_DIR}/server.pid"))"
else
  needs_build=0
  if [[ ! -f "${SERVER_JAR}" ]]; then
    needs_build=1
  elif find "${ROOT_DIR}/server/src" "${ROOT_DIR}/server/pom.xml" "${ROOT_DIR}/pom.xml" -newer "${SERVER_JAR}" -print -quit | grep -q .; then
    needs_build=1
  fi
  if [[ "${needs_build}" -eq 1 ]]; then
    echo "building server…"
    timeout 300s mvn -q -pl server -am package -DskipTests -Dkotlin.compiler.execution.strategy=in-process
  fi
  echo "starting server on :${SERVER_PORT}…"
  (
    cd "${ROOT_DIR}"
    PORT="${SERVER_PORT}" setsid java -jar "${SERVER_JAR}" >"${LOG_DIR}/server.log" 2>&1 &
    echo $! >"${RUN_DIR}/server.pid"
  )
fi

if [[ -f "${RUN_DIR}/client.pid" ]] && kill -0 "$(cat "${RUN_DIR}/client.pid")" 2>/dev/null; then
  echo "client already running (pid $(cat "${RUN_DIR}/client.pid"))"
else
  if [[ ! -d "${ROOT_DIR}/client/node_modules" ]]; then
    echo "installing client dependencies…"
    (cd "${ROOT_DIR}/client" && npm install)
  fi
  echo "starting client on :${CLIENT_PORT}…"
  (
    cd "${ROOT_DIR}/client"
    setsid npm run dev -- --host 127.0.0.1 --port "${CLIENT_PORT}" >"${LOG_DIR}/client.log" 2>&1 &
    echo $! >"${RUN_DIR}/client.pid"
  )
fi

server_ready=0
client_ready=0
for _ in $(seq 1 60); do
  if [[ "${server_ready}" -eq 0 ]] && curl -fsS "http://127.0.0.1:${SERVER_PORT}/api/v1/health" >/dev/null 2>&1; then
    server_ready=1
  fi
  if [[ "${client_ready}" -eq 0 ]] && curl -fsS "http://127.0.0.1:${CLIENT_PORT}/" >/dev/null 2>&1; then
    client_ready=1
  fi
  if [[ "${server_ready}" -eq 1 && "${client_ready}" -eq 1 ]]; then
    break
  fi
  sleep 0.5
done

if [[ "${server_ready}" -eq 0 ]]; then
  echo "server did not become ready; log tail:" >&2
  tail -n 40 "${LOG_DIR}/server.log" >&2 || true
  exit 1
fi
if [[ "${client_ready}" -eq 0 ]]; then
  echo "client did not become ready; log tail:" >&2
  tail -n 40 "${LOG_DIR}/client.log" >&2 || true
  exit 1
fi

echo "ICEJA-RTS is up:"
echo "  client: http://127.0.0.1:${CLIENT_PORT}"
echo "  server: http://127.0.0.1:${SERVER_PORT}/api/v1/health"
