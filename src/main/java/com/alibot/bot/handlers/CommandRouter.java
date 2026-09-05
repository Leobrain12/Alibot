package com.alibot.bot.handlers;

import com.alibot.bot.BotSender;
import com.alibot.bot.OrderPresenter;
import com.alibot.bot.keyboard.Keyboards;
import com.alibot.config.AppProperties;
import com.alibot.config.BotConfiguredCondition;
import com.alibot.domain.Order;
import com.alibot.domain.ReferenceCategory;
import com.alibot.domain.ReferenceItem;
import com.alibot.domain.User;
import com.alibot.service.AuthenticatedActor;
import com.alibot.service.OrderService;
import com.alibot.service.ReferenceDataService;
import com.alibot.service.StatsService;
import com.alibot.service.UserManagementService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

/** ТЗ п.109 — команды вторичны, но /start, /new_order, /stats, /help должны работать. */
@Component
@Conditional(BotConfiguredCondition.class)
@RequiredArgsConstructor
public class CommandRouter {

    private final CreateOrderWizard createOrderWizard;
    private final OrderService orderService;
    private final com.alibot.service.LeadService leadService;
    private final ReferenceDataService referenceDataService;
    private final UserManagementService userManagementService;
    private final StatsService statsService;
    private final OrderPresenter presenter;
    private final AppProperties appProperties;
    private final BotSender sender;

    public void handle(String command, long chatId, long telegramUserId, AuthenticatedActor actor) {
        String cmd = command.split("\\s")[0].split("@")[0];
        switch (cmd) {
            // Команда — не нажатие кнопки, редактировать нечего, поэтому editMessageId=null
            // (обычная отправка нового сообщения) во всех ветках ниже.
            case "/start" -> sendMainMenu(chatId, null, actor);
            case "/new_order" -> {
                if (!actor.isAdmin()) {
                    sender.send(chatId, "Команда доступна только администратору.");
                    return;
                }
                createOrderWizard.start(chatId, telegramUserId, null);
            }
            case "/stats" -> sendStats(chatId, null, actor);
            case "/help" -> sender.send(chatId, "Основная работа — через кнопки. /new_order — создать заявку, "
                    + "/stats — статистика.");
            default -> sender.send(chatId, "Неизвестная команда. /help — список команд.");
        }
    }

    /** editMessageId — id сообщения с кнопкой, которое привело на эту "страницу" (правим его на
     *  месте вместо отправки нового, чтобы навигация по меню не копилась в чате отдельными
     *  сообщениями), либо null, если страница открыта не по кнопке (например /start). */
    public void sendMainMenu(long chatId, Integer editMessageId, AuthenticatedActor actor) {
        InlineKeyboardMarkup menu;
        if (actor.isAdmin()) {
            List<String[]> rows = new ArrayList<>(List.of(
                    new String[]{"Новая заявка", "MENU_NEW_ORDER"},
                    new String[]{"Активные заказы", "MENU_ACTIVE"},
                    new String[]{"Нераспределённые", "MENU_UNASSIGNED"},
                    new String[]{"Лиды", "MENU_LEADS"},
                    new String[]{"История", "MENU_HISTORY"},
                    new String[]{"Мастера", "MENU_MASTERS"},
                    new String[]{"Поиск", "MENU_SEARCH"},
                    new String[]{"Статистика", "MENU_STATS"}));
            // Справочники и Пользователи — только SUPERADMIN (ТЗ п.5.1/132), как и в Mini App.
            if (actor.isSuperAdmin()) {
                rows.add(new String[]{"Справочники", "MENU_REFERENCE"});
                rows.add(new String[]{"Пользователи", "MENU_USERS"});
            }
            menu = Keyboards.singleColumn(rows);
        } else {
            menu = Keyboards.of(
                    "Активные заказы", "MENU_ACTIVE",
                    "История", "MENU_HISTORY",
                    "Моя статистика", "MENU_STATS");
        }
        menu = withMiniAppRow(menu);
        sender.editOrSend(chatId, editMessageId, actor.isAdmin() ? "Меню администратора:" : "Меню мастера:", menu);
    }

    /** Добавляет кнопку открытия Mini App первой строкой, если задан app.mini-app.base-url
     *  (Telegram открывает Web App только по HTTPS — см. README про ngrok/cloudflared для теста). */
    private InlineKeyboardMarkup withMiniAppRow(InlineKeyboardMarkup menu) {
        String baseUrl = appProperties.getMiniApp().getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return menu;
        }
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(Keyboards.webAppButton("🖥 Открыть Mini App", baseUrl + "/miniapp/index.html")));
        rows.addAll(menu.getKeyboard());
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /** Список заказов — это N отдельных карточек с собственными кнопками действий, а не одна
     *  "страница", поэтому в одно отредактированное сообщение не сворачивается: правим само меню,
     *  из которого сюда пришли (чтобы оно не оставалось висеть), а карточки шлём новыми
     *  сообщениями, как и раньше. */
    public void sendActiveList(long chatId, Integer editMessageId, AuthenticatedActor actor) {
        List<Order> orders = orderService.listActive(actor);
        if (orders.isEmpty()) {
            sender.editOrSend(chatId, editMessageId, "Активных заказов нет.", backTo("MENU_MAIN"));
            return;
        }
        sender.editOrSend(chatId, editMessageId, "Активные заказы:", backTo("MENU_MAIN"));
        for (Order order : orders) {
            sender.send(chatId, presenter.renderCard(order), presenter.actionsFor(order, actor));
        }
    }

    public void sendHistoryList(long chatId, Integer editMessageId, AuthenticatedActor actor) {
        List<Order> orders = orderService.listHistory(actor);
        if (orders.isEmpty()) {
            sender.editOrSend(chatId, editMessageId, "История пуста.", backTo("MENU_MAIN"));
            return;
        }
        StringBuilder sb = new StringBuilder("История заказов:\n");
        for (Order order : orders) {
            sb.append("#%d — %s (%s)\n".formatted(order.getNumber(), order.getStatus(), order.getVisitDate()));
        }
        sender.editOrSend(chatId, editMessageId, sb.toString(), backTo("MENU_MAIN"));
    }

    public void sendUnassignedList(long chatId, Integer editMessageId, AuthenticatedActor actor) {
        List<Order> orders = orderService.listUnassigned(actor);
        if (orders.isEmpty()) {
            sender.editOrSend(chatId, editMessageId, "Нераспределённых заказов нет.", backTo("MENU_MAIN"));
            return;
        }
        sender.editOrSend(chatId, editMessageId, "Нераспределённые заказы:", backTo("MENU_MAIN"));
        for (Order order : orders) {
            sender.send(chatId, presenter.renderCard(order), Keyboards.withBack(
                    Keyboards.of("Назначить мастера", "CHM:" + order.getId()), "MENU_MAIN"));
        }
    }

    /** ТЗ п.10-11 — обработка лида (дозаполнение полей заказа, конвертация) требует полноценной
     *  формы, которой в чате нет — здесь только список-уведомление, сама работа с лидом в Mini App. */
    public void sendLeadsList(long chatId, Integer editMessageId, AuthenticatedActor actor) {
        List<com.alibot.domain.Lead> leads = leadService.listPending(actor);
        if (leads.isEmpty()) {
            sender.editOrSend(chatId, editMessageId, "Необработанных лидов нет.", backTo("MENU_MAIN"));
            return;
        }
        StringBuilder sb = new StringBuilder("Необработанные лиды (%d):\n".formatted(leads.size()));
        for (var lead : leads) {
            sb.append("— %s, %s%s\n".formatted(lead.getCustomerName(), lead.getCustomerPhone(),
                    lead.getSource() != null ? " (" + lead.getSource() + ")" : ""));
        }
        sb.append("\nОбработать (конвертировать в заявку или отклонить) — в Mini App, раздел «Лиды».");
        sender.editOrSend(chatId, editMessageId, sb.toString(), backTo("MENU_MAIN"));
    }

    /** Клавиатура из одной кнопки "◀ Назад" — для страниц, у которых кроме неё нет других кнопок. */
    private InlineKeyboardMarkup backTo(String callbackData) {
        return Keyboards.withBack(Keyboards.of(), callbackData);
    }

    /** ТЗ п.132 — то же управление справочниками, что в Mini App (SUPERADMIN), но в чате: список
     *  категорий → значения категории с переключением активности и добавлением новых.
     *  Проверка роли не дублируется здесь — её делает ReferenceDataService.listAll(). */
    public void sendReferenceCategories(long chatId, Integer editMessageId, AuthenticatedActor actor) {
        List<ReferenceItem> all = referenceDataService.listAll(actor);
        List<String[]> options = new ArrayList<>();
        for (ReferenceCategory category : ReferenceCategory.values()) {
            long count = all.stream().filter(i -> i.getCategory() == category).count();
            options.add(new String[]{"%s (%d)".formatted(category.label(), count), "REFCAT:" + category.name()});
        }
        sender.editOrSend(chatId, editMessageId, "Справочники:", Keyboards.withBack(Keyboards.singleColumn(options), "MENU_MAIN"));
    }

    public void sendReferenceItems(long chatId, Integer editMessageId, ReferenceCategory category, AuthenticatedActor actor) {
        List<ReferenceItem> items = referenceDataService.listAll(actor).stream()
                .filter(i -> i.getCategory() == category)
                .toList();
        StringBuilder sb = new StringBuilder(category.label() + ":\n");
        if (items.isEmpty()) {
            sb.append("(пусто)\n");
        }
        List<String[]> options = new ArrayList<>();
        for (ReferenceItem item : items) {
            sb.append(item.isActive() ? "🟢 " : "⚪ ").append(item.getValue()).append('\n');
            options.add(new String[]{(item.isActive() ? "Скрыть: " : "Показать: ") + item.getValue(),
                    "REFTOG:" + item.getId() + ":" + !item.isActive()});
        }
        options.add(new String[]{"+ Добавить значение", "REFADD:" + category.name()});
        sender.editOrSend(chatId, editMessageId, sb.toString(),
                Keyboards.withBack(Keyboards.singleColumn(options), "MENU_REFERENCE"));
    }

    /** ТЗ п.5.1/9 — управление пользователями из чата, а не только через Mini App (которая
     *  сейчас недоступна без домена/HTTPS). MASTER здесь намеренно не создаётся — ему ещё нужен
     *  отдельный профиль мастера (тип/размер выплаты), для которого в чате пока нет формы;
     *  роль MASTER заводится через Mini App или напрямую через REST API. */
    public void sendUsersList(long chatId, Integer editMessageId, AuthenticatedActor actor) {
        List<User> users = userManagementService.list(actor);
        StringBuilder sb = new StringBuilder("Пользователи:\n");
        List<String[]> options = new ArrayList<>();
        for (User u : users) {
            sb.append("%s%s — %s, tg:%d%s\n".formatted(u.isActive() ? "🟢 " : "⚪ ", u.getName(), u.getRole(),
                    u.getTelegramUserId(), u.getPhone() != null ? ", " + u.getPhone() : ""));
            options.add(new String[]{(u.isActive() ? "Деактивировать: " : "Активировать: ") + u.getName(),
                    "USERTOG:" + u.getId() + ":" + !u.isActive()});
        }
        options.add(new String[]{"+ Добавить пользователя", "USERADD"});
        sender.editOrSend(chatId, editMessageId, sb.toString(), Keyboards.withBack(Keyboards.singleColumn(options), "MENU_MAIN"));
    }

    public void sendStats(long chatId, Integer editMessageId, AuthenticatedActor actor) {
        sendStats(chatId, editMessageId, actor, "TODAY");
    }

    /** ТЗ п.73/76 — выбор периода (сегодня / 7 дней / 30 дней) через кнопки под самим сообщением;
     *  произвольный диапазон API уже поддерживает (from/to) — здесь как чат-интерфейс годятся
     *  только пресеты, свободный ввод дат текстом хуже кнопок и уже есть в Mini App. */
    public void sendStats(long chatId, Integer editMessageId, AuthenticatedActor actor, String period) {
        Instant to = Instant.now();
        Instant from = switch (period) {
            case "7D" -> to.minus(7, ChronoUnit.DAYS);
            case "30D" -> to.minus(30, ChronoUnit.DAYS);
            default -> to.truncatedTo(ChronoUnit.DAYS);
        };
        String periodLabel = switch (period) {
            case "7D" -> "7 дней";
            case "30D" -> "30 дней";
            default -> "сегодня";
        };
        InlineKeyboardMarkup periodKeyboard = Keyboards.withBack(Keyboards.grid(List.of(
                new String[]{"Сегодня", "STATS:TODAY"},
                new String[]{"7 дней", "STATS:7D"},
                new String[]{"30 дней", "STATS:30D"}), 3), "MENU_MAIN");

        if (actor.isAdmin()) {
            var stats = statsService.overallStats(from, to, actor);
            sender.editOrSend(chatId, editMessageId, """
                    Итоги за %s
                    Новых заказов: %d
                    Выполнено: %d
                    Выручка: %s ₽
                    Средний чек: %s ₽
                    Ожидают деталь: %d
                    Отменено клиентом: %d
                    Гарантийных: %d
                    """.formatted(periodLabel, stats.ordersCreated(), stats.completedOrders(), stats.revenue(),
                    stats.avgCheck(), stats.waitingPart(), stats.customerCancellations(), stats.warrantyOrders()),
                    periodKeyboard);
        } else {
            var stats = statsService.masterStats(actor.masterId(), from, to, actor);
            sender.editOrSend(chatId, editMessageId, """
                    Моя статистика за %s
                    Назначено: %d
                    Принято: %d
                    Выполнено: %d
                    Отказов: %d
                    Выручка: %s ₽
                    Средний чек: %s ₽
                    Гарантийных: %d
                    """.formatted(periodLabel, stats.assignedOrders(), stats.acceptedOrders(), stats.completedOrders(),
                    stats.declinedOrders(), stats.revenue(), stats.avgCheck(), stats.warrantyOrders()),
                    periodKeyboard);
        }
    }
}
