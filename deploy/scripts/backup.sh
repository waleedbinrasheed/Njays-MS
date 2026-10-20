#!/bin/sh
# Database backups for Leading Publishers Inventory (runs in the "backup" container).
#   sh /scripts/backup.sh loop   once a day, the first time the PC is on after BACKUP_HOUR
#   sh /scripts/backup.sh now    one backup immediately
# Files: /backups/leadingims-YYYY-MM-DD_HHMM.dump (pg_dump custom format), older than
# BACKUP_KEEP_DAYS are deleted. On Windows the folder is the "backups" folder next to .env.
set -eu

export PGPASSWORD="$POSTGRES_PASSWORD"
HOUR="${BACKUP_HOUR:-2}"
KEEP="${BACKUP_KEEP_DAYS:-14}"

dump() {
  file="/backups/leadingims-$(date +%Y-%m-%d_%H%M).dump"
  pg_dump -h db -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --file="$file.part"
  mv "$file.part" "$file"
  echo "$(date '+%Y-%m-%d %H:%M') backup written: $(basename "$file") ($(du -h "$file" | cut -f1))"
  find /backups -name 'leadingims-*.dump' -mtime +"$KEEP" -exec rm -f {} \; -exec echo "removed old backup {}" \;
}

case "${1:-loop}" in
  now)
    dump
    ;;
  loop)
    echo "Daily backups after $HOUR:00 into the backups folder, kept $KEEP days."
    while true; do
      today="$(date +%Y-%m-%d)"
      hour="$(date +%H | sed 's/^0//')"
      if [ "${hour:-0}" -ge "$HOUR" ] && ! ls /backups/leadingims-"${today}"_*.dump >/dev/null 2>&1; then
        dump || echo "$(date '+%Y-%m-%d %H:%M') BACKUP FAILED - will retry in 10 minutes"
      fi
      sleep 600
    done
    ;;
  *)
    echo "usage: backup.sh loop|now" >&2
    exit 2
    ;;
esac
