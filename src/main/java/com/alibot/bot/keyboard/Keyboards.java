package com.alibot.bot.keyboard;

import java.util.ArrayList;
import java.util.List;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;

/** Билдеры inline-клавиатур. Основной UX идёт через кнопки, а не команды (ТЗ п.109). */
public final class Keyboards {

    private Keyboards() {
    }

    public static InlineKeyboardButton button(String text, String callbackData) {
        return InlineKeyboardButton.builder().text(text).callbackData(callbackData).build();
    }

    /** Кнопка, открывающая Mini App внутри Telegram (требует HTTPS-адрес — Telegram не
     *  открывает Web App по http). */
    public static InlineKeyboardButton webAppButton(String text, String url) {
        return InlineKeyboardButton.builder().text(text).webApp(WebAppInfo.builder().url(url).build()).build();
    }

    /** Один вариант в строке — удобно для длинных подписей (список мастеров, заказы). */
    public static InlineKeyboardMarkup singleColumn(List<String[]> textAndData) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (String[] pair : textAndData) {
            rows.add(new InlineKeyboardRow(button(pair[0], pair[1])));
        }
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /** По columns кнопок в строке — удобно для коротких вариантов (тип техники, слоты). */
    public static InlineKeyboardMarkup grid(List<String[]> textAndData, int columns) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        InlineKeyboardRow current = new InlineKeyboardRow();
        for (String[] pair : textAndData) {
            current.add(button(pair[0], pair[1]));
            if (current.size() == columns) {
                rows.add(current);
                current = new InlineKeyboardRow();
            }
        }
        if (!current.isEmpty()) {
            rows.add(current);
        }
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    public static InlineKeyboardMarkup of(String... textDataPairs) {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (int i = 0; i < textDataPairs.length; i += 2) {
            rows.add(new InlineKeyboardRow(button(textDataPairs[i], textDataPairs[i + 1])));
        }
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }
}
