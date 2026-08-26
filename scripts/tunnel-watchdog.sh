#!/usr/bin/env bash
# localtunnel периодически рвёт соединение сам по себе (известная особенность бесплатного
# сервиса) — этот скрипт проверяет здоровье туннеля раз в 30 секунд и перезапускает
# `npx localtunnel`, если тот умер или туннель отвечает не 200. Держит поддомен фиксированным,
# чтобы Menu Button в Telegram не нужно было перенастраивать при каждом обрыве.

SUBDOMAIN="poor-planes-lay"
PORT=8099
URL="https://${SUBDOMAIN}.loca.lt"
PIDFILE="/tmp/localtunnel.pid"
LOGFILE="/tmp/localtunnel.log"

stop_tunnel() {
  # Критично: убить старый процесс ПЕРЕД запуском нового — иначе два клиента начинают
  # конкурировать за один поддомен у localtunnel-сервера, и оба работают нестабильно
  # (это и произошло при первом запуске вотчдога).
  local pid
  pid=$(cat "$PIDFILE" 2>/dev/null)
  if [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1; then
    kill "$pid" 2>/dev/null
    sleep 1
  fi
}

start_tunnel() {
  stop_tunnel
  nohup npx localtunnel --port "$PORT" --subdomain "$SUBDOMAIN" > "$LOGFILE" 2>&1 &
  echo $! > "$PIDFILE"
  sleep 5
}

is_alive() {
  local pid
  pid=$(cat "$PIDFILE" 2>/dev/null)
  [ -n "$pid" ] && ps -p "$pid" > /dev/null 2>&1
}

# Один неудачный запрос не повод перезапускать — учитываем сетевые дребезги.
is_healthy() {
  for _ in 1 2; do
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" --max-time 8 "$URL/miniapp/index.html")
    if [ "$code" = "200" ]; then
      return 0
    fi
    sleep 3
  done
  return 1
}

echo "Watchdog запущен: $URL"
while true; do
  if ! is_alive || ! is_healthy; then
    echo "$(date -Iseconds) туннель недоступен — перезапуск"
    start_tunnel
  fi
  sleep 30
done
