package com.alibot.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/** Отдельный бин RestTemplate для HttpCrmSyncGateway — существует, только если CRM настроена
 *  (см. CrmConfiguredCondition), и доступен для теста как обычный бин, к которому можно
 *  привязать MockRestServiceServer. */
@Configuration
public class CrmHttpClientConfig {

    @Bean
    @Conditional(CrmConfiguredCondition.class)
    public RestTemplate crmRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}
