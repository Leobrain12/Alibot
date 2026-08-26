# Alibot — Telegram-операционка сервиса ремонта

Java/Spring Boot backend + Telegram-бот + Mini App для распределения заявок между мастерами
сервиса ремонта бытовой техники. Реализовано по ТЗ (`Техническое_задание_Telegram_бота...docx`)
в объёме **core-flow** — см. «Что не входит» ниже.

## Архитектура

```
Telegram (боты-хендлеры)  ─┐
                            ├─▶  service/*  (вся бизнес-логика, единственный источник правды)  ─▶ PostgreSQL
REST API (сайт/CRM, Mini App) ─┘
```

Ключевое: `bot/handlers/*` и `api/controller/*` — тонкие адаптеры. Оба резолвят "кто вызывает"
(`AuthenticatedActor`) и вызывают один и тот же `OrderService`/`OrderStatusMachine`/
`AccessControlService`. Ни бот, ни REST-контроллеры не содержат правил валидности переходов
статуса или прав доступа — это исключительно в `service/`.

- `domain/` — JPA-сущности и enum'ы.
- `repository/` — Spring Data JPA.
- `service/` — бизнес-логика: `OrderService`, `OrderStatusMachine` (таблица переходов статуса),
  `WorkReportService`, `MediaService` (+ `storage/MediaStorage` — порт, `LocalFileSystemMediaStorage` —
  единственный адаптер сейчас), `PaymentService`, `StatsService`, `ConversationStateService`
  (server-side FSM для многошаговых сценариев бота), `IdempotencyService`, `AccessControlService`,
  `NotificationGateway` (порт — единственная реализация `bot.TelegramNotificationGateway`).
- `bot/` — Telegram-бот (`org.telegram:telegrambots` 9.x, long polling по умолчанию, webhook
  реализован в `WebhookController`, переключается `bot.mode`).
- `api/` — REST API (Internal API из ТЗ п.88 + то же самое API использует Mini App).
- `miniapp/` — валидация `Telegram.WebApp.initData` (HMAC-SHA256 по документированному алгоритму).
- `src/main/resources/static/miniapp/index.html` — Mini App: стиль/вёрстка унаследованы от
  присланного HTML-прототипа (`tg_service_clickable_prototype.html`), но вместо `state` в памяти —
  реальные вызовы REST API с заголовком `Telegram-Init-Data`.

## Запуск локально (без Telegram-токена — проверить REST API/БД)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Поднимется на H2 (файл `./data/alibot-dev`), без бота (он не стартует без `TELEGRAM_BOT_TOKEN`).
Внутренний API-ключ для проверки: `dev-api-key`.

```bash
curl -H "X-Internal-Api-Key: dev-api-key" http://localhost:8080/api/v1/masters
```

Пользователей/мастеров в БД по умолчанию нет — создайте их напрямую в H2-консоли
(`http://localhost:8080/h2-console`, JDBC URL как в `application.yml`, профиль `dev`) либо через
`/api/v1/users` (только SUPERADMIN, см. бутстрап ниже).

## Запуск с ботом и PostgreSQL

```bash
docker run --name alibot-db -e POSTGRES_DB=alibot -e POSTGRES_USER=alibot -e POSTGRES_PASSWORD=alibot -p 5432:5432 -d postgres:16

export DB_URL=jdbc:postgresql://localhost:5432/alibot
export DB_USER=alibot
export DB_PASSWORD=alibot
export TELEGRAM_BOT_TOKEN=<токен от @BotFather>
export SUPERADMIN_TELEGRAM_ID=<ваш telegram_user_id>
export INTERNAL_API_KEY=<секрет для сайта/CRM>

./mvnw spring-boot:run
```

При старте с заданным `SUPERADMIN_TELEGRAM_ID` создаётся первый пользователь с ролью SUPERADMIN
(ТЗ п.6.1 — без этого никто не может попасть в систему; самостоятельная регистрация запрещена).
Напишите боту `/start` от этого аккаунта, дальше создавайте ADMIN/MASTER через `/api/v1/users`.

### Открыть Mini App внутри Telegram

В @BotFather: `/mybots` → выбрать бота → `Bot Settings` → `Menu Button` → указать URL вида
`https://<ваш-домен>/miniapp/index.html` (домен должен быть за HTTPS — для локальной проверки
подойдёт туннель типа ngrok).

## Production-развёртывание

`Dockerfile` + `docker-compose.yml` (приложение + PostgreSQL) + `deploy/README.md` — пошаговая
инструкция от пустого VPS до бота с постоянным доменом, HTTPS и webhook вместо polling.
Локально сейчас проект гоняется на dev-профиле с H2 — для боевого запуска нужен именно этот путь.

## Obsidian-vault (просмотр реальных данных)

`obsidian-vault/` — не документация, а витрина текущих заказов/мастеров как markdown-заметок
со свойствами, чтобы смотреть/фильтровать их в Obsidian. Синхронизируется из работающего
backend через тот же REST API, которым пользуется Mini App:

```powershell
./scripts/sync-obsidian.ps1
```

Подробности и схема свойств — в `obsidian-vault/README.md`.

## Тесты

```bash
./mvnw test
```

- `OrderStatusMachineTest` — таблица переходов статуса (ТЗ п.15).
- `WebAppInitDataValidatorTest` — HMAC-проверка `initData` (валидная/подделанная подпись/протухшая).
- `OrderLifecycleIntegrationTest` — полный цикл заказа через реальные Spring-бины и H2:
  создание → назначение → принятие → выезд → диагностика → согласование цены → ремонт → отчёт → оплата.
- `AlibotApplicationTests` — контекст поднимается даже без токена бота.

## Что реализовано (core-flow)

Полный операционный цикл заказа, роли и авторизация по `telegram_user_id`, история статусов,
повторные визиты/гарантия, недозвон/перенос, обязательный WorkReport, фото/видео (включая
Telegram-альбомы) в приватном хранилище с лимитом 20 МБ, полная/частичная оплата, базовая
статистика по формулам ТЗ п.75, идемпотентность Telegram-апдейтов, server-side FSM для
многошаговых сценариев бота, Mini App поверх того же REST API.

## Что осознанно не входит в этот заход

- Реальная интеграция с CRM (`CRMAdapter`) — есть только поля `lead_id`/`crm_id`/`source` на
  заказе для будущей трассируемости.
- Экспорт CSV/XLSX.
- `AuditLog` как отдельная таблица (история статусов есть, общего аудита действий — нет).
- Продовый мониторинг/алертинг, задокументированная процедура backup/recovery.
- Юридическая модель ПД (152-ФЗ: размещение БД в РФ, согласование юристом схемы передачи
  контактов клиента мастеру через Telegram) — см. ТЗ п.96-98, требует решения бизнеса/юриста
  до продакшена, не техническая задача.
- В Mini App часть многошаговых мастер-сценариев (диагностика/цена/деталь) сделаны через
  `prompt()`-диалоги браузера, а не полноценные формы — акцент сделан на бота (там это
  полноценный server-side FSM с инлайн-клавиатурами), Mini App в первую очередь — дашборд
  просмотра/поиска/управления для администратора.
