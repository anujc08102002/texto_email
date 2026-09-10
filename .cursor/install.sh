#!/usr/bin/env bash
#
# Idempotent Cloud Agent install phase for the Texto Email Platform.
# Prepares durable, source-derived state: Docker Engine, local env files,
# the backend build, and frontend dependencies. Long-running services are
# NOT started here (see start.sh / terminals).
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

log() { printf '\n=== %s ===\n' "$1"; }

# ---------------------------------------------------------------------------
# 1. Docker Engine + Compose (needed for Postgres, RabbitMQ, Redis, Mailpit).
#    Installed here so it is baked into the environment build snapshot.
# ---------------------------------------------------------------------------
if ! command -v docker >/dev/null 2>&1; then
  log "Installing Docker Engine"
  sudo DEBIAN_FRONTEND=noninteractive apt-get update -qq
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
    ca-certificates curl gnupg fuse-overlayfs uidmap
  # A pending interactive fuse3 conffile prompt can wedge dpkg; resolve it.
  sudo DEBIAN_FRONTEND=noninteractive dpkg --configure -a --force-confold || true

  sudo install -m 0755 -d /etc/apt/keyrings
  if [ ! -f /etc/apt/keyrings/docker.gpg ]; then
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
      | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    sudo chmod a+r /etc/apt/keyrings/docker.gpg
  fi
  . /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
  sudo DEBIAN_FRONTEND=noninteractive apt-get update -qq
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
    docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
else
  log "Docker already installed ($(docker --version))"
fi

# Nested Cloud Agent VMs cannot use the default overlay2 driver; fuse-overlayfs
# works unprivileged. Also use legacy iptables for bridge networking.
sudo mkdir -p /etc/docker
echo '{ "storage-driver": "fuse-overlayfs" }' | sudo tee /etc/docker/daemon.json >/dev/null
sudo update-alternatives --set iptables /usr/sbin/iptables-legacy >/dev/null 2>&1 || true
sudo update-alternatives --set ip6tables /usr/sbin/ip6tables-legacy >/dev/null 2>&1 || true
# Allow the agent user to talk to the daemon without sudo (effective next login).
sudo groupadd -f docker
sudo usermod -aG docker "$(id -un)" || true

# ---------------------------------------------------------------------------
# 2. Local environment files (never overwrite existing, never commit secrets).
# ---------------------------------------------------------------------------
log "Preparing local env files"
[ -f .env ] || cp .env.example .env
[ -f frontend/.env.local ] || cp frontend/.env.example frontend/.env.local

# ---------------------------------------------------------------------------
# 3. Backend build (Java 21 / Maven wrapper). Downloads deps and produces jar.
# ---------------------------------------------------------------------------
log "Building backend"
if [ -z "${JAVA_HOME:-}" ] && [ -d /usr/lib/jvm/java-21-openjdk-amd64 ]; then
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
fi
chmod +x backend/mvnw
( cd backend && ./mvnw -q -B -DskipTests package )

# ---------------------------------------------------------------------------
# 4. Frontend dependencies (Node 20+). Uses the committed lockfile.
# ---------------------------------------------------------------------------
log "Installing frontend dependencies"
( cd frontend && npm ci )

log "Install phase complete"
