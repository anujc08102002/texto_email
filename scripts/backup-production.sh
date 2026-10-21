#!/usr/bin/env bash
# Consistent single-host backup: brief application maintenance window.
# Queue messages are NOT included. For migration, drain old queues before switching hosts.
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo 'Run this script with sudo.' >&2; exit 1; }
cd /opt/texto-email
umask 077
backup_root=/var/backups/texto-email
stamp=$(date -u +%Y%m%dT%H%M%SZ)
backup_dir="$backup_root/$stamp"
mkdir -p "$backup_dir"
chmod 700 "$backup_root" "$backup_dir"
backend_active=false
frontend_active=false
systemctl is-active --quiet texto-email-backend && backend_active=true
systemctl is-active --quiet texto-email-frontend && frontend_active=true
restart_apps() {
  if $backend_active; then systemctl start texto-email-backend; fi
  if $frontend_active; then systemctl start texto-email-frontend; fi
}
trap restart_apps EXIT
systemctl stop texto-email-frontend texto-email-backend
compose=(docker compose --env-file /etc/texto-email/dependencies.env -f infrastructure/production/dependencies.compose.yml)
"${compose[@]}" exec -T postgres sh -c 'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$backup_dir/database.dump"
# Empty/truncated dumps are a failed backup, not a successful snapshot.
[[ -s "$backup_dir/database.dump" ]]
tar -C /var/lib/texto-email -czf "$backup_dir/attachments.tar.gz" attachments
tar -C /etc -czf "$backup_dir/configuration-SECRETS.tar.gz" texto-email
"${compose[@]}" exec -T rabbitmq rabbitmqctl export_definitions /tmp/texto-definitions.json >/dev/null
"${compose[@]}" exec -T rabbitmq cat /tmp/texto-definitions.json > "$backup_dir/rabbitmq-definitions.json"
(cd "$backup_dir" && sha256sum database.dump attachments.tar.gz configuration-SECRETS.tar.gz rabbitmq-definitions.json > SHA256SUMS)
echo "Backup created at $backup_dir"
echo 'Contains secrets. Encrypt and copy off-host. RabbitMQ definitions do not include queued messages.'
echo 'No old backup was deleted. Verify restore in a separate test environment.'
