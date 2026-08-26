package com.alibot.api.security;

import com.alibot.config.AppProperties;
import com.alibot.config.SystemActorBootstrap;
import com.alibot.domain.Role;
import com.alibot.repository.UserRepository;
import com.alibot.service.AuthenticatedActor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** ТЗ п.17.2/88 — внешние системы (сайт/CRM) вызывают Internal API с заголовком X-Internal-Api-Key. */
@Component
@RequiredArgsConstructor
public class InternalApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Api-Key";

    private final AppProperties appProperties;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key != null && key.equals(appProperties.getInternalApiKey())) {
            userRepository.findByTelegramUserId(SystemActorBootstrap.SYSTEM_TELEGRAM_ID).ifPresent(systemUser -> {
                AuthenticatedActor actor = new AuthenticatedActor(systemUser.getId(), null, Role.ADMIN, null);
                SecurityContextHolder.getContext().setAuthentication(new ActorAuthentication(actor));
            });
        }
        chain.doFilter(request, response);
    }
}
