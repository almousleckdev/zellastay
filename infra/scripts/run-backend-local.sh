#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="$root/infra/docker/.env"

if [[ ! -f "$env_file" ]]; then
  echo "Missing $env_file. Copy infra/docker/.env.example and set values." >&2
  exit 1
fi

set -a
# shellcheck source=/dev/null
source "$env_file"
set +a

cd "$root/backend"
exec ./gradlew bootRun
