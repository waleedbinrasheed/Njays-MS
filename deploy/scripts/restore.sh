#!/bin/sh
# Restores a backup into the database (runs in the "backup" container; restore.bat calls it with
# the API and web app stopped). Everything in the database is replaced by the backup.
#   sh /scripts/restore.sh leadingims-2026-10-20_0200.dump
set -eu

name="$(basename "${1:?usage: restore.sh <backup file name>}")"
file="/backups/$name"
[ -f "$file" ] || { echo "Backup not found: $name (it must be in the backups folder)" >&2; exit 1; }

export PGPASSWORD="$POSTGRES_PASSWORD"
echo "Restoring $name ..."
pg_restore -h db -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --single-transaction "$file"
echo "Restore finished."
