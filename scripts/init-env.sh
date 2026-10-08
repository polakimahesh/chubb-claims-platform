#!/usr/bin/env bash
# Creates .env with random secrets if it does not exist yet. Usage: ./scripts/init-env.sh
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -f .env ]; then echo ".env already exists - leaving it unchanged"; exit 0; fi
rand() { head -c 24 /dev/urandom | base64 | tr -d '/+=\n' | head -c 24; }
{
  echo "DB_USERNAME=claims"
  echo "DB_PASSWORD=$(rand)"
  echo "APP_SECURITY_DEMO_PASSWORD=$(rand)"
} > .env
echo "Created .env (git-ignored). Demo-user password: see APP_SECURITY_DEMO_PASSWORD in .env"
