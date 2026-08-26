# Production-развёртывание alibot

Пошаговая инструкция от пустого VPS до работающего бота с постоянным доменом, PostgreSQL и
HTTPS. Ничего из этого не запускается автоматически — каждый шаг вы выполняете сами на сервере;
здесь просто точный маршрут, чтобы не собирать его по кускам.

## 0. Что понадобится купить

- **VPS** (виртуальный сервер), Ubuntu 22.04/24.04. Для этого проекта с запасом хватает
  младшего тарифа: 1–2 vCPU, 2 ГБ RAM, 20+ ГБ диска (Postgres + медиафайлы заказов).
  Подойдёт любой провайдер с почасовой/помесячной оплатой — Hetzner, Timeweb, Selectel,
  DigitalOcean и т.п.; выбор конкретного — на ваше усмотрение (это шаг, который делаете вы сами
  через их сайт, регистрацию аккаунта за вас никто не сделает).
- **Домен** — у любого регистратора (reg.ru, Namecheap и т.п.), либо поддомен, если он уже
  у вас есть.

## 1. DNS

В панели регистратора добавьте A-запись:

```
ВАШ_ДОМЕН   A   <IP-адрес сервера>
```

Проверить, что применилось (может занять до часа):
```bash
nslookup ВАШ_ДОМЕН
```

## 2. Подготовка сервера

Подключитесь по SSH и поставьте Docker:

```bash
ssh root@<IP-адрес сервера>
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
```

## 3. Перенос кода на сервер

Если проект уже в git-репозитории (GitHub/GitLab и т.п.):
```bash
git clone <ваш-репозиторий> /opt/alibot
```

Если репозитория нет — скопируйте файлы с локальной машины:
```bash
rsync -avz --exclude target --exclude data --exclude .git \
  "путь/к/alibot/" root@<IP-адрес сервера>:/opt/alibot/
```

## 4. Настройка .env

```bash
cd /opt/alibot
cp .env.example .env
nano .env
```

Обязательно заполните: `DB_PASSWORD`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_BOT_USERNAME`,
`SUPERADMIN_TELEGRAM_ID`, `INTERNAL_API_KEY`. `BOT_MODE` пока оставьте `polling` — на webhook
переключим в шаге 7, после того как заработает HTTPS.

## 5. Первый запуск

```bash
docker compose up -d --build
docker compose logs -f app
```

В логе должно появиться `Telegram bot запущен в режиме long polling` и
`Создан начальный SUPERADMIN с telegram_user_id=...` (второе — один раз, при первом запуске на
пустой базе). Проверка, что приложение живо:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8080/miniapp/index.html
```

Должно быть `200`.

## 6. nginx + HTTPS (Let's Encrypt)

```bash
apt update && apt install -y nginx certbot python3-certbot-nginx
cp deploy/nginx.conf.template /etc/nginx/sites-available/alibot
sed -i 's/ВАШ_ДОМЕН/<ваш-домен>/' /etc/nginx/sites-available/alibot
ln -s /etc/nginx/sites-available/alibot /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx

certbot --nginx -d <ваш-домен>
```

Certbot сам допишет HTTPS-блок в конфиг nginx и настроит автопродление сертификата. Проверка:

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://<ваш-домен>/miniapp/index.html
```

## 7. Переключение бота на webhook

Пока бот работал на polling — это нормально и для продакшена, но ТЗ рекомендует webhook, когда
HTTPS уже есть. Правим `.env`:

```
BOT_MODE=webhook
TELEGRAM_WEBHOOK_BASE_URL=https://ВАШ_ДОМЕН
TELEGRAM_WEBHOOK_SECRET=<сгенерируйте длинную случайную строку>
MINI_APP_BASE_URL=https://ВАШ_ДОМЕН
```

```bash
docker compose up -d  # пересоздаст app с новыми переменными
docker compose logs -f app
```

В логе должно появиться `Telegram webhook установлен: https://ВАШ_ДОМЕН/telegram/webhook/...`
и `Menu Button Mini App настроена`.

## 8. Проверка

- В Telegram: `/start` у бота — должно открыться меню.
- Кнопка Menu Button / «🖥 Открыть Mini App» — должна открыть Mini App прямо в Telegram, уже без
  временного туннеля.
- `curl -s https://ВАШ_ДОМЕН/actuator/health` — должно вернуть `{"status":"UP"}` с деталями по
  `db` и `telegramBot` (ТЗ п.104, см. шаг 9).

## 9. Бэкап и восстановление (ТЗ п.105/106)

Скрипты `scripts/backup-db.sh` / `scripts/restore-db.sh` уже в репозитории. Ежедневный бэкап через cron:

```bash
chmod +x scripts/backup-db.sh scripts/restore-db.sh
crontab -e
# добавить строку (бэкап каждую ночь в 03:00, хранить 30 дней):
0 3 * * * COMPOSE_DIR=/opt/alibot BACKUP_DIR=/opt/alibot/backups RETENTION_DAYS=30 /opt/alibot/scripts/backup-db.sh >> /var/log/alibot-backup.log 2>&1
```

**Восстановление** (например, после аварии на новом сервере — сначала шаги 0–5 этой инструкции,
затем):

```bash
./scripts/restore-db.sh /opt/alibot/backups/alibot-2026-01-15_030000.sql.gz
```

Скрипт остановит `app`, пересоздаст БД из дампа и запустит `app` заново. Запросит подтверждение
(`yes`) — действие необратимо перезаписывает текущую БД.

**Важно:** сама процедура восстановления должна быть хотя бы раз протестирована до продакшена
(ТЗ п.106) — не просто написана. Проверка ниже (раздел «Проверка процедуры восстановления»)
показывает, что оба скрипта реально работают на настоящем PostgreSQL, а не только в теории.

## Дальше

- Обновление после правок в коде: `git pull` (или повторный rsync) → `docker compose up -d --build`.
- Docker перезапускает контейнеры сам при падении (`restart: unless-stopped`) и при перезагрузке
  сервера — отдельно ничего настраивать не нужно, если `docker.service` включён в автозагрузку
  (шаг 2 это уже сделал).
- Мониторинг (ТЗ п.104): `GET /actuator/health` — публичный (для внешнего uptime-монитора вроде
  UptimeRobot/Healthchecks.io, без авторизации), `GET /actuator/metrics` — требует
  `X-Internal-Api-Key`. Подключить внешний аптайм-монитор на `/actuator/health` — отдельный шаг,
  сам сервис я тоже не могу завести за вас (нужен аккаунт).
- Экспорт заказов (ТЗ п.80): `GET /api/v1/orders/export?format=csv|xlsx&from=&to=&status=` (или
  кнопка «Скачать CSV/XLSX» на экране «Статистика» в Mini App) — только ADMIN/SUPERADMIN.
- CRM-синхронизация (ТЗ п.85-87): задайте `CRM_WEBHOOK_URL`/`CRM_WEBHOOK_SECRET` в `.env`, когда
  появится реальная CRM с приёмным вебхуком — события заказа (создание/смена статуса/оплата)
  начнут доставляться туда с ретраями. До этого момента ничего никуда не отправляется. Неудачные
  после исчерпания попыток доставки видны через `GET /api/v1/crm-sync/failed` и перезапускаются
  вручную через `POST /api/v1/crm-sync/{id}/retry`.
