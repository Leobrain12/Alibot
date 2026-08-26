package com.alibot.bot;

import com.alibot.config.BotConfiguredCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

/** Точка входа long polling — чистый адаптер, вся логика в UpdateDispatcher. */
@Component
@Conditional(BotConfiguredCondition.class)
@RequiredArgsConstructor
public class AlibotTelegramBot implements LongPollingSingleThreadUpdateConsumer {

    private final UpdateDispatcher dispatcher;

    @Override
    public void consume(Update update) {
        dispatcher.dispatch(update);
    }
}
