#!/usr/bin/env bash
# ТЗ п.106 — восстановление из бэкапа. Останавливает приложение (не БД), пересоздаёт данные,
# запускает приложение обратно. Требует явного подтверждения — необратимо перезаписывает БД.
set -euo pipefail

if [ $# -ne 1 ]; then
  echo "Использование: $0 <путь-к-alibot-*.sql.gz>"
  exit 1
fi

BACKUP_FILE="$1"
COMPOSE_DIR="${COMPOSE_DIR:-/opt/alibot}"
DB_USER=$(grep -E '^DB_USER=' "$COMPOSE_DIR/.env" | cut -d= -f2- || echo alibot)

if [ ! -f "$BACKUP_FILE" ]; then
  echo "Файл не найден: $BACKUP_FILE"
  exit 1
fi

echo "Это ПЕРЕЗАПИШЕТ текущую базу данными из $BACKUP_FILE. Продолжить? (yes/no)"
read -r CONFIRM
if [ "$CONFIRM" != "yes" ]; then
  echo "Отменено."
  exit 0
fi

cd "$COMPOSE_DIR"
# Что бы ни случилось дальше (в том числе Ctrl+C или ошибка psql), app не должен остаться
# лежать остановленным — это как раз то, что случилось при первом тесте этого скрипта.
trap 'docker compose start app' EXIT

docker compose stop app

# Два отдельных вызова psql, не один -c с несколькими операторами через ";" — PostgreSQL
# неявно оборачивает многооператорную строку в одной simple-query message в транзакцию,
# а DROP DATABASE/CREATE DATABASE внутри транзакции не выполняются в принципе.
docker compose exec -T db psql -U "$DB_USER" -d postgres -c "DROP DATABASE IF EXISTS alibot;"
docker compose exec -T db psql -U "$DB_USER" -d postgres -c "CREATE DATABASE alibot OWNER $DB_USER;"

gunzip -c "$BACKUP_FILE" | docker compose exec -T db psql -U "$DB_USER" alibot

echo "Восстановление завершено, приложение перезапускается (см. trap)."
