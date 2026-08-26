package com.alibot.api.security;

import com.alibot.service.AuthenticatedActor;
import com.alibot.service.exception.ForbiddenException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Достаёт AuthenticatedActor из SecurityContext — единая точка для контроллеров. */
@Component
public class CurrentActor {

    public AuthenticatedActor get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof ActorAuthentication actorAuth) {
            return actorAuth.actor();
        }
        throw new ForbiddenException("Не аутентифицировано");
    }
}
