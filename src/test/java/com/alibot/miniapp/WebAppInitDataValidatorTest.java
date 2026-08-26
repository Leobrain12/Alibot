package com.alibot.miniapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** Проверяем оба направления: валидная подпись принимается, подделанная — нет. */
class WebAppInitDataValidatorTest {

    private final WebAppInitDataValidator validator = new WebAppInitDataValidator(new ObjectMapper());
    private static final String BOT_TOKEN = "123456:TEST-TOKEN";

    @Test
    void acceptsValidSignatureAndExtractsTelegramUserId() throws Exception {
        String initData = buildSignedInitData(777L, Instant.now().getEpochSecond(), BOT_TOKEN);

        Optional<Long> result = validator.validateAndExtractTelegramUserId(initData, BOT_TOKEN);

        assertThat(result).contains(777L);
    }

    @Test
    void rejectsTamperedHash() throws Exception {
        String initData = buildSignedInitData(777L, Instant.now().getEpochSecond(), BOT_TOKEN);
        String tampered = initData.replace("777", "999");

        Optional<Long> result = validator.validateAndExtractTelegramUserId(tampered, BOT_TOKEN);

        assertThat(result).isEmpty();
    }

    @Test
    void rejectsWrongBotToken() throws Exception {
        String initData = buildSignedInitData(777L, Instant.now().getEpochSecond(), BOT_TOKEN);

        Optional<Long> result = validator.validateAndExtractTelegramUserId(initData, "other:token");

        assertThat(result).isEmpty();
    }

    @Test
    void rejectsStaleAuthDate() throws Exception {
        long twoDaysAgo = Instant.now().getEpochSecond() - (2 * 24 * 60 * 60);
        String initData = buildSignedInitData(777L, twoDaysAgo, BOT_TOKEN);

        Optional<Long> result = validator.validateAndExtractTelegramUserId(initData, BOT_TOKEN);

        assertThat(result).isEmpty();
    }

    private static String buildSignedInitData(long telegramUserId, long authDate, String botToken) throws Exception {
        String userJson = "{\"id\":" + telegramUserId + ",\"first_name\":\"Test\"}";
        String dataCheckString = "auth_date=" + authDate + "\nquery_id=AA\nuser=" + userJson;

        byte[] secretKey = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), botToken.getBytes(StandardCharsets.UTF_8));
        byte[] hash = hmac(secretKey, dataCheckString.getBytes(StandardCharsets.UTF_8));
        String hex = toHex(hash);

        return "auth_date=" + authDate
                + "&query_id=AA"
                + "&user=" + URLEncoder.encode(userJson, StandardCharsets.UTF_8)
                + "&hash=" + hex;
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
