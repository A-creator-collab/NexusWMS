#!/usr/bin/env bash
# Consistent online backup of the NexusWMS database (InnoDB snapshot, no table locks).
# Usage: NEXUS_DB_PASSWORD=... ./scripts/backup-mysql.sh [backup-dir]   (defaults: db=nexuswms user=nexus host=localhost keep=14 days)
set -euo pipefail
DB="${NEXUS_DB_NAME:-nexuswms}"; USER="${NEXUS_DB_USER:-nexus}"; HOST="${NEXUS_DB_HOST:-localhost}"; DIR="${1:-./backups}"; KEEP="${NEXUS_BACKUP_KEEP_DAYS:-14}"
mkdir -p "$DIR"; umask 077
OUT="$DIR/${DB}-$(date +%Y%m%d-%H%M%S).sql.gz"
MYSQL_PWD="${NEXUS_DB_PASSWORD:-}" mysqldump --single-transaction --routines --triggers --default-character-set=utf8mb4 -h "$HOST" -u "$USER" "$DB" | gzip > "$OUT"
gzip -t "$OUT"                                   # fail loudly if the archive is corrupt
find "$DIR" -name "${DB}-*.sql.gz" -mtime +"$KEEP" -delete
echo "Backup written: $OUT ($(du -h "$OUT" | cut -f1))"
