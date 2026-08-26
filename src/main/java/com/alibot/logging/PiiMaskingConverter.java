package com.alibot.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import java.util.regex.Pattern;

/**
 * ТЗ п.103 — не писать телефон/адрес открытым текстом в error logs. Аудит текущих log.*
 * вызовов показал, что нигде явно не логируется customerPhone/address (используется только
 * order.getNumber()) — это правило перестраховывает от будущей регрессии: любая строка,
 * похожая на телефонный номер, маскируется на уровне форматирования лога, а не полагается на
 * дисциплину каждого будущего log.info(...).
 */
public class PiiMaskingConverter extends ClassicConverter {

    // Достаточно длинная последовательность цифр (с возможными +/-/пробелами/скобками) —
    // телефонные номера в разных форматах (+7 999 123-45-67, 89991234567 и т.п.).
    private static final Pattern PHONE_LIKE = Pattern.compile("(\\+?\\d[\\d\\-\\s()]{8,17}\\d)");

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null) {
            return "";
        }
        return PHONE_LIKE.matcher(message).replaceAll("[phone-masked]");
    }
}
