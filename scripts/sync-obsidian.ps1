<#
.SYNOPSIS
    Синхронизирует реальные данные alibot (заказы, мастера) в Obsidian-vault как
    markdown-заметки с YAML frontmatter.

.DESCRIPTION
    Тянет текущее состояние через тот же REST API (Internal API, ТЗ п.88), которым
    пользуется Mini App и внешние системы — никакого отдельного доступа к БД в обход
    сервисного слоя. Перезаписывает заметки идемпотентно (по id/number), так что скрипт
    можно гонять сколько угодно раз — например, добавить в планировщик задач.

.PARAMETER BaseUrl
    Адрес запущенного backend. По умолчанию локальный dev-профиль на 8099.

.PARAMETER ApiKey
    Значение X-Internal-Api-Key (app.internal-api-key). По умолчанию dev-профильный ключ.

.PARAMETER VaultPath
    Путь к папке vault. По умолчанию ../obsidian-vault рядом со скриптом.

.EXAMPLE
    ./scripts/sync-obsidian.ps1
    ./scripts/sync-obsidian.ps1 -BaseUrl "http://localhost:8080" -ApiKey $env:INTERNAL_API_KEY
#>
param(
    [string]$BaseUrl = "http://localhost:8099",
    [string]$ApiKey = "dev-api-key",
    [string]$VaultPath = (Join-Path (Split-Path $PSScriptRoot -Parent) "obsidian-vault")
)

$ErrorActionPreference = "Stop"
$Headers = @{ "X-Internal-Api-Key" = $ApiKey }

function Invoke-JsonUtf8 {
    # Windows PowerShell 5.1's Invoke-RestMethod/-WebRequest mis-detects charset when the
    # server (Spring, application/json без charset) doesn't declare one, and silently falls
    # back to Latin-1 for Cyrillic — вместо "Другое" получается "ÐÑÑÐ³Ð¾Ðµ". Обходим это,
    # читая сырые байты ответа и декодируя их как UTF-8 вручную, а не полагаясь на автоопределение.
    param([string]$Uri, [hashtable]$Headers)
    $resp = Invoke-WebRequest -Uri $Uri -Headers $Headers -Method Get -UseBasicParsing
    $text = [System.Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
    if ([string]::IsNullOrWhiteSpace($text)) { return @() }
    return $text | ConvertFrom-Json
}

function Write-Utf8NoBom {
    param([string]$Path, [string]$Content)
    $dir = Split-Path $Path -Parent
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Sanitize-FileName {
    param([string]$Name)
    if ([string]::IsNullOrWhiteSpace($Name)) { return "unnamed" }
    $clean = $Name -replace '[\\/:*?"<>|#\[\]]', ''
    $clean = $clean.Trim()
    if ($clean.Length -eq 0) { return "unnamed" }
    return $clean
}

function Yaml-List {
    param([object[]]$Items, [string]$IndentKey)
    if (-not $Items -or $Items.Count -eq 0) { return "${IndentKey}: []" }
    $lines = @("${IndentKey}:")
    foreach ($i in $Items) { $lines += "  - `"$i`"" }
    return ($lines -join "`n")
}

Write-Host "== alibot -> Obsidian sync ==" -ForegroundColor Cyan
Write-Host "Backend: $BaseUrl"
Write-Host "Vault:   $VaultPath"

# --- Мастера ---
Write-Host "`nЗагружаю мастеров..."
$masters = @(Invoke-JsonUtf8 -Uri "$BaseUrl/api/v1/masters" -Headers $Headers) | Where-Object { $_ }
$masterLinkById = @{}

foreach ($m in $masters) {
    $shortId = $m.id.Substring(0, 8)
    $fileTitle = "$(Sanitize-FileName $m.name) ($shortId)"
    $masterLinkById[$m.id] = $fileTitle

    $activeStr = if ($m.active) { "true" } else { "false" }
    $content = @"
---
type: master
id: $($m.id)
name: "$($m.name)"
phone: "$($m.phone)"
status: $($m.status)
active: $activeStr
$(Yaml-List -Items $m.applianceTypes -IndentKey "appliance_types")
$(Yaml-List -Items $m.brands -IndentKey "brands")
$(Yaml-List -Items $m.geoZones -IndentKey "geo_zones")
tags: [master, "status/$($m.status)"]
---

# $($m.name)

**Статус:** $($m.status)$(if (-not $m.active) { " _(неактивен)_" })
**Телефон:** $($m.phone)

## Заказы этого мастера

``````dataview
TABLE status as "Статус", visit_date as "Дата", customer as "Клиент"
FROM "Заказы"
WHERE master = this.file.link
SORT visit_date DESC
``````

*Блок выше — запрос [Dataview](obsidian://show-plugin?id=dataview) (community-плагин).
Без него заказы мастера видны через панель Backlinks в правой панели Obsidian.*
"@
    Write-Utf8NoBom -Path (Join-Path $VaultPath "Мастера/$fileTitle.md") -Content $content
}
Write-Host "  -> $($masters.Count) заметок в Мастера/"

# --- Заказы (активные + история) ---
Write-Host "`nЗагружаю заказы..."
$active = @(Invoke-JsonUtf8 -Uri "$BaseUrl/api/v1/orders?view=active" -Headers $Headers) | Where-Object { $_ }
$history = @(Invoke-JsonUtf8 -Uri "$BaseUrl/api/v1/orders?view=history" -Headers $Headers) | Where-Object { $_ }
$orders = @($active) + @($history)

foreach ($o in $orders) {
    $masterLine = 'master: ""'
    $masterBodyLine = ""
    if ($o.masterId -and $masterLinkById.ContainsKey($o.masterId)) {
        $masterLine = "master: `"[[$($masterLinkById[$o.masterId])]]`""
        $masterBodyLine = "**Мастер:** [[$($masterLinkById[$o.masterId])]]  "
    }

    $content = @"
---
type: order
id: $($o.id)
number: $($o.number)
status: $($o.status)
customer: "$($o.customerName)"
phone: "$($o.customerPhone)"
appliance: "$($o.applianceType)"
brand: "$($o.brand)"
model: "$($o.model)"
address: "$($o.address)"
visit_date: $($o.visitDate)
time_from: "$($o.timeFrom)"
time_to: "$($o.timeTo)"
$masterLine
final_price: $($o.finalPrice)
amount_paid: $($o.amountPaid)
amount_due: $($o.amountDue)
created_at: $($o.createdAt)
tags: [order, "status/$($o.status)"]
---

# Заказ #$($o.number)

**Статус:** $($o.status)
**Техника:** $($o.applianceType)$(if ($o.brand) { " · $($o.brand)" })
**Проблема:** $($o.symptom)
**Клиент:** $($o.customerName), $($o.customerPhone)
**Адрес:** $($o.address)
**Визит:** $($o.visitDate), $($o.timeFrom)–$($o.timeTo)
$masterBodyLine
$(if ($o.finalPrice) { "**Итого:** $($o.finalPrice) ₽ (оплачено $($o.amountPaid), долг $($o.amountDue))" })
"@
    Write-Utf8NoBom -Path (Join-Path $VaultPath "Заказы/Order-$($o.number).md") -Content $content
}
Write-Host "  -> $($orders.Count) заметок в Заказы/ ($($active.Count) активных, $($history.Count) в истории)"

Write-Host "`nГотово." -ForegroundColor Green
