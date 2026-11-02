#!/usr/bin/env bash
# Creates throwaway local identity secrets in the git-ignored infra/docker/secrets directory.
# Never reuse these values outside a developer machine.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
dir="$root/infra/docker/secrets"
key="$dir/ZS_IDENTITY_TOKEN_SIGNING_KEY"
pepper="$dir/ZS_IDENTITY_CHALLENGE_PEPPER"
mfa="$dir/ZS_IDENTITY_MFA_ENCRYPTION_KEY"

if [[ -e "$key" || -e "$pepper" || -e "$mfa" ]]; then
  echo "Secrets already exist in $dir; delete them first to regenerate." >&2
  exit 1
fi

mkdir -p "$dir"
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$key"
openssl rand -hex 32 | tr -d '\n' > "$pepper"
openssl rand -base64 32 | tr -d '\n' > "$mfa"
# Readable by the container user (uid 10001); local development only.
chmod 755 "$dir"
chmod 644 "$key" "$pepper" "$mfa"
echo "Created $key, $pepper and $mfa"
