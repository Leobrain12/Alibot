package com.alibot.miniapp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Проверка Telegram.WebApp.initData по документированному алгоритму:
 * secret_key = HMAC_SHA256(key="WebAppData", data=bot_token);
 * hash = hex(HMAC_SHA256(key=secret_key, data=data_check_string)),
 * где data_check_string — все поля initData кроме hash, отсортированные по ключу и
 * склеенные как "key=value" через '\n'.
 */
@Component
@RequiredArgsConstructor
public class WebAppInitDataValidator {

    private static final long MAX_AGE_SECONDS = 24 * 60 * 60;
    private final ObjectMapper objectMapper;

    public Optional<Long> validateAndExtractTelegramUserId(String initData, String botToken) {
        if (initData == null || initData.isBlank() || botToken == null || botToken.isBlank()) {
            return Optional.empty();
        }
        List<String[]> pairs = new ArrayList<>();
        String providedHash = null;
        for (String part : initData.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            String key = URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            if ("hash".equals(key)) {
                providedHash = value;
            } else {
                pairs.add(new String[]{key, value});
            }
        }
        if (providedHash == null) {
            return Optional.empty();
        }
        pairs.sort((a, b) -> a[0].compareTo(b[0]));
        StringBuilder dataCheckString = new StringBuilder();
        String authDate = null;
        String userJson = null;
        for (String[] pair : pairs) {
            if (!dataCheckString.isEmpty()) {
                dataCheckString.append('\n');
            }
            dataCheckString.append(pair[0]).append('=').append(pair[1]);
            if ("auth_date".equals(pair[0])) authDate = pair[1];
            if ("user".equals(pair[0])) userJson = pair[1];
        }

        try {
            byte[] secretKey = hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8), botToken.getBytes(StandardCharsets.UTF_8));
            byte[] computed = hmacSha256(secretKey, dataCheckString.toString().getBytes(StandardCharsets.UTF_8));
            String computedHex = toHex(computed);
            if (!MessageDigest.isEqual(computedHex.getBytes(StandardCharsets.UTF_8), providedHash.getBytes(StandardCharsets.UTF_8))) {
                return Optional.empty();
            }
            if (authDate != null) {
                long authEpoch = Long.parseLong(authDate);
                if (Instant.now().getEpochSecond() - authEpoch > MAX_AGE_SECONDS) {
                    return Optional.empty();
                }
            }
            if (userJson == null) {
                return Optional.empty();
            }
            JsonNode user = objectMapper.readTree(userJson);
            return Optional.of(user.get("id").asLong());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
