#!/usr/bin/env bash
# Restore a backup made by backup-mysql.sh into an EMPTY database. Stop the app first.
# Usage: NEXUS_DB_PASSWORD=... ./scripts/restore-mysql.sh backups/nexuswms-YYYYMMDD-HHMMSS.sql.gz
set -euo pipefail
[ $# -eq 1 ] || { echo "usage: $0 <backup.sql.gz>"; exit 2; }
DB="${NEXUS_DB_NAME:-nexuswms}"; USER="${NEXUS_DB_USER:-nexus}"; HOST="${NEXUS_DB_HOST:-localhost}"
read -r -p "This overwrites data in '$DB' on $HOST. Type the database name to continue: " ans
[ "$ans" = "$DB" ] || { echo "aborted"; exit 1; }
gzip -dc "$1" | MYSQL_PWD="${NEXUS_DB_PASSWORD:-}" mysql -h "$HOST" -u "$USER" "$DB"
echo "Restore complete."
