package com.agentforge.code.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Webhook HMAC-SHA256 签名验证。
 *
 * <p>Gitee Webhook 配置密码（secret）后，请求头 {@code X-Gitee-Token} 携带 HMAC 签名
 * （Gitee 直接用 secret 作为 token，或用 secret 对 payload 计算 HMAC，取决于配置）。
 *
 * <p>本实现兼容两种 Gitee 模式：
 * <ol>
 *   <li>直接比对模式：token == 配置的 secret</li>
 *   <li>HMAC 模式：X-Gitee-Token == HMAC-SHA256(payload, secret)</li>
 * </ol>
 *
 * <p>secret 从配置项 {@code agentforge.webhook.secret} 读取；为空则跳过校验（仅开发环境）。
 */
@Slf4j
@Component
public class WebhookSignature {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** Webhook 密钥，配置在 application.yml: agentforge.webhook.secret */
    private final String secret;

    public WebhookSignature(@Value("${agentforge.webhook.secret:}") String secret) {
        this.secret = secret;
    }

    /**
     * 验证签名。
     *
     * @param token       请求头 X-Gitee-Token 的值
     * @param rawPayload  原始请求体（用于 HMAC 计算）
     * @return true 通过
     */
    public boolean verify(String token, String rawPayload) {
        // 开发环境未配置 secret，放行（生产必须配置）
        if (secret == null || secret.isBlank()) {
            log.warn("Webhook secret 未配置，跳过签名验证（仅开发环境允许）");
            return true;
        }
        if (token == null || token.isBlank()) {
            log.warn("Webhook 请求缺少 X-Gitee-Token");
            return false;
        }

        // 模式1：直接比对
        if (constantTimeEquals(token, secret)) {
            return true;
        }
        // 模式2：HMAC 比对
        String expected = hmacSha256(rawPayload, secret);
        boolean ok = expected != null && constantTimeEquals(token, expected);
        if (!ok) {
            log.warn("Webhook 签名验证失败");
        }
        return ok;
    }

    /** HMAC-SHA256 并转 hex。 */
    private String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(raw);
        } catch (Exception e) {
            log.error("HMAC 计算失败", e);
            return null;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /** 常量时间比较，防时序攻击。 */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ba.length != bb.length) return false;
        int diff = 0;
        for (int i = 0; i < ba.length; i++) {
            diff |= ba[i] ^ bb[i];
        }
        return diff == 0;
    }
}
