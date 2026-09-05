#!/usr/bin/env bash
# ТЗ п.105 — ежедневный backup БД, retention 14-30 дней. Рассчитан на запуск на сервере рядом
# с docker-compose.yml (см. deploy/README.md) через cron, но проверяемо запускается и вручную.
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/alibot/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
COMPOSE_DIR="${COMPOSE_DIR:-/opt/alibot}"

mkdir -p "$BACKUP_DIR"
STAMP=$(date +%F_%H%M%S)
FILE="$BACKUP_DIR/alibot-$STAMP.sql.gz"

# DB_USER берём из .env рядом с docker-compose.yml, чтобы не дублировать значение здесь.
DB_USER=$(grep -E '^DB_USER=' "$COMPOSE_DIR/.env" | cut -d= -f2- || echo alibot)

docker compose -f "$COMPOSE_DIR/docker-compose.yml" exec -T db \
  pg_dump -U "$DB_USER" alibot | gzip > "$FILE"

echo "Бэкап сохранён: $FILE ($(du -h "$FILE" | cut -f1))"

# Ротация — удаляем бэкапы старше RETENTION_DAYS.
find "$BACKUP_DIR" -name 'alibot-*.sql.gz' -mtime "+$RETENTION_DAYS" -delete
echo "Бэкапов в каталоге: $(ls "$BACKUP_DIR"/alibot-*.sql.gz 2>/dev/null | wc -l)"
