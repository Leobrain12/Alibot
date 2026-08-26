package com.alibot.config;

import com.alibot.api.security.InternalApiKeyAuthFilter;
import com.alibot.api.security.TelegramInitDataAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * REST API (/api/v1/**) требует аутентификации через один из двух фильтров: InternalApiKeyAuthFilter
 * (сайт/CRM) или TelegramInitDataAuthFilter (Mini App) — см. их описание. Всё остальное (Mini App
 * статика, Telegram webhook — у него своя защита через secret_token, H2-консоль в dev) открыто.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final InternalApiKeyAuthFilter internalApiKeyAuthFilter;
    private final TelegramInitDataAuthFilter telegramInitDataAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        // health/info нужны открытыми для uptime-мониторинга (ТЗ п.104) — сам
                        // мониторинг снаружи не имеет ни internal-api-key, ни Telegram initData.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").authenticated()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(internalApiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(telegramInitDataAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
