---
type: master
id: ee70ec9d-7bbb-492b-8d53-0420455e8e8d
name: "Сидор Волков"
phone: "+79161234503"
status: ACTIVE
active: true
appliance_types: []
brands: []
geo_zones: []
tags: [master, "status/ACTIVE"]
---

# Сидор Волков

**Статус:** ACTIVE
**Телефон:** +79161234503

## Заказы этого мастера

```dataview
TABLE status as "Статус", visit_date as "Дата", customer as "Клиент"
FROM "Заказы"
WHERE master = this.file.link
SORT visit_date DESC
```

*Блок выше — запрос [Dataview](obsidian://show-plugin?id=dataview) (community-плагин).
Без него заказы мастера видны через панель Backlinks в правой панели Obsidian.*