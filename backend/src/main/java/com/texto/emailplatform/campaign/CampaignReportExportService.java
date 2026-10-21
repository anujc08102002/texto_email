package com.texto.emailplatform.campaign;

import com.texto.emailplatform.campaign.domain.CampaignRepository;
import com.texto.emailplatform.common.exception.ApiException;
import com.texto.emailplatform.tenant.web.TenantContext;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Service
public class CampaignReportExportService {
    private final CampaignRepository campaigns;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public CampaignReportExportService(CampaignRepository campaigns, JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.campaigns = campaigns; this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setReadOnly(true);
    }

    public StreamingResponseBody export(UUID campaignId) {
        // Capture authorized scope before the request thread and its tenant context are released.
        UUID tenant = TenantContext.get().orElseThrow(() -> new ApiException(401, "UNAUTHENTICATED", "Authentication is required"));
        campaigns.findByIdAndTenantId(campaignId, tenant)
                .orElseThrow(() -> new ApiException(404, "CAMPAIGN_NOT_FOUND", "Campaign was not found"));
        return output -> {
            var writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
            writer.write("email,status,error,added_at\r\n");
            transactions.executeWithoutResult(status -> jdbc.query(connection -> {
                var statement = connection.prepareStatement("SELECT r.email, " + CampaignRecipientStates.EXPRESSION
                        + " AS state, COALESCE(r.error_message, m.last_error, '') AS error, r.created_at"
                        + " FROM campaign_recipients r LEFT JOIN email_messages m ON m.id = r.email_message_id AND m.tenant_id = r.tenant_id"
                        + " WHERE r.campaign_id = ? AND r.tenant_id = ? ORDER BY r.created_at, r.id");
                statement.setObject(1, campaignId); statement.setObject(2, tenant); statement.setFetchSize(500);
                return statement;
            }, (org.springframework.jdbc.core.RowCallbackHandler) row -> {
                try {
                    writer.write(csv(row.getString("email")) + "," + csv(row.getString("state")) + ","
                            + csv(row.getString("error")) + "," + csv(row.getTimestamp("created_at").toInstant().toString()) + "\r\n");
                } catch (IOException exception) { throw new UncheckedIOException(exception); }
            }));
            writer.flush();
        };
    }

    static String csv(String value) {
        if (value == null) value = "";
        String trimmed = value.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
