#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT_DIR"

if podman compose version >/dev/null 2>&1; then
    compose=(podman compose)
elif command -v podman-compose >/dev/null 2>&1; then
    compose=(podman-compose)
elif docker compose version >/dev/null 2>&1; then
    compose=(docker compose)
else
    printf '%s\n' 'No Compose provider found. Install Podman Compose or Docker Compose.' >&2
    exit 1
fi

if [[ ! -f .env ]]; then
    printf '%s\n' 'Notice: .env is absent; compose.yaml demo defaults will be used.'
    printf '%s\n' 'For explicit configuration: cp .env.example .env'
fi

"${compose[@]}" up --detach --build
"${compose[@]}" ps

frontend_binding=$("${compose[@]}" port frontend 8080 2>/dev/null || true)
if [[ -n "$frontend_binding" ]]; then
    printf 'Frontend: http://%s\n' "$frontend_binding"
else
    printf '%s\n' 'Frontend: see the published port in the compose ps output above.'
fi
printf '%s\n' 'OSRM may remain in "starting" state while Jiangsu data is prepared on first run.'
