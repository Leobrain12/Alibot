package com.alibot.api.security;

import com.alibot.service.AuthenticatedActor;
import java.util.Collections;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/** Оборачивает AuthenticatedActor в Spring Security Authentication — оба фильтра
 *  (InternalApiKeyAuthFilter, TelegramInitDataAuthFilter) кладут в контекст один и тот же тип,
 *  так что контроллеры не знают, откуда пришёл вызов. */
public class ActorAuthentication extends AbstractAuthenticationToken {

    private final AuthenticatedActor actor;

    public ActorAuthentication(AuthenticatedActor actor) {
        super(Collections.emptyList());
        this.actor = actor;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return actor;
    }

    public AuthenticatedActor actor() {
        return actor;
    }
}
