package com.texto.emailplatform.campaign;

import com.texto.emailplatform.common.exception.ApiException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignUnsubscribeService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final String publicBase;

    public CampaignUnsubscribeService(JdbcTemplate jdbc,
            @Value("${email-platform.campaign.public-base-url:http://localhost:8080}") String publicBase) {
        this.jdbc = jdbc;
        URI uri = URI.create(publicBase);
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
                && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))))) {
            throw new IllegalArgumentException("Campaign public base URL must use HTTPS (HTTP is allowed on localhost only)");
        }
        this.publicBase = publicBase.replaceAll("/+$", "");
    }

    @Transactional
    public String urlFor(UUID tenantId, String email) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        jdbc.update("INSERT INTO campaign_unsubscribe_tokens (token_hash, tenant_id, email) VALUES (?, ?, ?)",
                hash(token), tenantId, email);
        return publicBase + "/api/v1/public/unsubscribe/" + token;
    }

    @Transactional
    public void unsubscribe(String token) {
        if (token == null || !token.matches("[a-f0-9]{64}")) throw invalidToken();
        // Lock the token so repeated one-click requests are serialized and remain idempotent.
        var rows = jdbc.queryForList("SELECT tenant_id, email FROM campaign_unsubscribe_tokens WHERE token_hash = ? FOR UPDATE", hash(token));
        if (rows.isEmpty()) throw invalidToken();
        UUID tenantId = (UUID) rows.getFirst().get("tenant_id");
        String email = (String) rows.getFirst().get("email");
        jdbc.update("UPDATE contacts SET subscription_status = 'UNSUBSCRIBED', updated_at = CURRENT_TIMESTAMP WHERE tenant_id = ? AND email = ?",
                tenantId, email);
        jdbc.update("""
                INSERT INTO suppressions (id, tenant_id, email, normalized_email, reason, type, source, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'unsubscribe', 'UNSUBSCRIBE', 'CAMPAIGN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (tenant_id, normalized_email) DO NOTHING
                """, UUID.randomUUID(), tenantId, email, email);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static ApiException invalidToken() {
        return new ApiException(404, "INVALID_UNSUBSCRIBE_LINK", "This unsubscribe link is invalid");
    }
}
