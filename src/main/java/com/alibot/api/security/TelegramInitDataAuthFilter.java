package com.alibot.api.security;

import com.alibot.config.BotProperties;
import com.alibot.miniapp.WebAppInitDataValidator;
import com.alibot.service.ActorResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Mini App шлёт Telegram.WebApp.initData в заголовке — проверяем подпись и резолвим актёра
 *  так же, как и бот (ActorResolver — общая точка "кто это" для обоих транспортов). */
@Component
@RequiredArgsConstructor
public class TelegramInitDataAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Telegram-Init-Data";

    private final WebAppInitDataValidator validator;
    private final ActorResolver actorResolver;
    private final BotProperties botProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String initData = request.getHeader(HEADER);
            if (initData != null && botProperties.isConfigured()) {
                validator.validateAndExtractTelegramUserId(initData, botProperties.getToken())
                        .flatMap(actorResolver::resolve)
                        .ifPresent(actor -> SecurityContextHolder.getContext()
                                .setAuthentication(new ActorAuthentication(actor)));
            }
        }
        chain.doFilter(request, response);
    }
}
