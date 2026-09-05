package com.alibot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bot")
public class BotProperties {

    private String token;
    private String username;
    /** polling | webhook */
    private String mode = "polling";
    private String webhookBaseUrl;
    private String webhookSecretToken;

    /** Некоторые хостинги/дата-центры режут исходящий трафик к серверам Telegram (проверено на
     *  практике — DNS резолвится, но TCP-подключение висит по таймауту, хотя остальной интернет
     *  работает). Пусто по умолчанию — прокси не используется; задаётся только если реально
     *  понадобился обходной путь. HTTP или SOCKS5. */
    private String proxyHost;
    private Integer proxyPort;
    /** HTTP | SOCKS */
    private String proxyType = "HTTP";

    public boolean isProxyConfigured() {
        return proxyHost != null && !proxyHost.isBlank() && proxyPort != null && proxyPort > 0;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    public String getProxyType() {
        return proxyType;
    }

    public void setProxyType(String proxyType) {
        this.proxyType = proxyType;
    }

    public boolean isConfigured() {
        return token != null && !token.isBlank();
    }

    public boolean isWebhookMode() {
        return "webhook".equalsIgnoreCase(mode);
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getWebhookBaseUrl() {
        return webhookBaseUrl;
    }

    public void setWebhookBaseUrl(String webhookBaseUrl) {
        this.webhookBaseUrl = webhookBaseUrl;
    }

    public String getWebhookSecretToken() {
        return webhookSecretToken;
    }

    public void setWebhookSecretToken(String webhookSecretToken) {
        this.webhookSecretToken = webhookSecretToken;
    }
}
