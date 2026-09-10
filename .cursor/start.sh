#!/usr/bin/env bash
#
# Per-boot start phase for the Texto Email Platform.
# Reconciles the Docker daemon and brings up the backing services
# (Postgres, RabbitMQ, Redis, Mailpit), waiting until they are healthy.
# It then returns; the backend and frontend run as named terminals.
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

log() { printf '\n=== %s ===\n' "$1"; }

# ---------------------------------------------------------------------------
# 1. Start the Docker daemon if it is not already running (no systemd here).
# ---------------------------------------------------------------------------
if ! sudo docker info >/dev/null 2>&1; then
  log "Starting dockerd"
  sudo bash -c 'nohup dockerd >/var/log/dockerd.log 2>&1 &'
  for i in $(seq 1 60); do
    if sudo docker info >/dev/null 2>&1; then break; fi
    sleep 1
  done
  sudo docker info >/dev/null 2>&1 || { echo "dockerd failed to start"; tail -n 40 /var/log/dockerd.log || true; exit 1; }
fi
log "Docker ready ($(sudo docker version --format '{{.Server.Version}}' 2>/dev/null))"

# ---------------------------------------------------------------------------
# 2. Bring up infrastructure services (idempotent).
# ---------------------------------------------------------------------------
[ -f .env ] || cp .env.example .env
log "Starting infrastructure (postgres, rabbitmq, redis, mailpit)"
sudo docker compose --env-file .env -f infrastructure/docker-compose.yml up -d

# ---------------------------------------------------------------------------
# 3. Wait until every container reports healthy.
# ---------------------------------------------------------------------------
log "Waiting for services to become healthy"
for i in $(seq 1 60); do
  status="$(sudo docker compose --env-file .env -f infrastructure/docker-compose.yml ps --format '{{.Name}} {{.Health}}')"
  if [ -n "$status" ] && ! echo "$status" | grep -qvE 'healthy$'; then
    echo "$status"
    log "All infrastructure services healthy"
    exit 0
  fi
  sleep 5
done

echo "Timed out waiting for healthy services:"
sudo docker compose --env-file .env -f infrastructure/docker-compose.yml ps
exit 1
