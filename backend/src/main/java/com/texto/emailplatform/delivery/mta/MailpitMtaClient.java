package com.texto.emailplatform.delivery.mta;

import com.texto.emailplatform.common.config.EmailPlatformProperties;
import com.texto.emailplatform.common.logging.SensitiveDataMasker;
import jakarta.mail.Address;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Properties;
import org.eclipse.angus.mail.smtp.SMTPTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Local Mailpit SMTP implementation of {@link MtaClient}.
 *
 * <p>TLS: disabled by default (Mailpit plaintext on 1025). When STARTTLS is required,
 * Jakarta Mail will not silently downgrade. Certificate verification is never disabled.
 */
@Component
public class MailpitMtaClient implements MtaClient {

    private static final Logger log = LoggerFactory.getLogger(MailpitMtaClient.class);

    private final EmailPlatformProperties properties;

    public MailpitMtaClient(EmailPlatformProperties properties) {
        this.properties = properties;
    }

    @Override
    public String implementation() {
        return "mailpit";
    }

    @Override
    public MtaResult submit(MtaSubmitRequest request) {
        Instant started = Instant.now();
        SmtpEnvelope envelope = request.envelope();
        if (envelope.mailFrom() == null || envelope.mailFrom().isBlank()) {
            return MtaResult.failure(MtaOutcome.PERMANENT_FAILURE, false, "550", null, "MAIL FROM is required");
        }
        if (envelope.recipients().isEmpty()) {
            return MtaResult.failure(MtaOutcome.PERMANENT_FAILURE, false, "550", null, "No SMTP recipients");
        }

        Session session = Session.getInstance(smtpProperties(envelope.mailFrom()));
        session.setDebug(false);
        SMTPTransport transport = null;
        try {
            ImmutableSmtpMessage message = new ImmutableSmtpMessage(
                    session,
                    request.rfc822(),
                    sanitizeAddress(envelope.mailFrom())
            );
            Address[] recipients = new Address[envelope.recipients().size()];
            for (int i = 0; i < envelope.recipients().size(); i++) {
                recipients[i] = new InternetAddress(sanitizeAddress(envelope.recipients().get(i)), false);
            }
            transport = (SMTPTransport) session.getTransport(properties.getMta().getSsl().isEnabled() ? "smtps" : "smtp");
            connect(transport);
            transport.sendMessage(message, recipients);
            String lastServerResponse = transport.getLastServerResponse();
            String smtpCode = transport.getLastReturnCode() > 0
                    ? String.valueOf(transport.getLastReturnCode())
                    : "250";
            MtaResult result = MtaResult.success(smtpCode, lastServerResponse, request.messageId());
            logResult(result, started, envelope);
            return result;
        } catch (Exception exception) {
            MtaResult result = SmtpExceptionClassifier.classify(exception);
            logResult(result, started, envelope);
            return result;
        } finally {
            closeQuietly(transport);
        }
    }

    private void connect(SMTPTransport transport) throws Exception {
        EmailPlatformProperties.Smtp smtp = properties.getMta().getSmtp();
        String username = smtp.getUsername();
        String password = smtp.getPassword();
        boolean auth = username != null && !username.isBlank();
        transport.connect(
                properties.resolvedMtaHost(),
                properties.resolvedMtaPort(),
                auth ? username : null,
                auth ? password : null
        );
    }

    private Properties smtpProperties(String envelopeFrom) {
        EmailPlatformProperties.Mta mta = properties.getMta();
        EmailPlatformProperties.Smtp smtp = mta.getSmtp();
        Properties props = new Properties();
        String protocol = mta.getSsl().isEnabled() ? "smtps" : "smtp";
        props.put("mail.transport.protocol", protocol);
        props.put("mail." + protocol + ".host", properties.resolvedMtaHost());
        props.put("mail." + protocol + ".port", String.valueOf(properties.resolvedMtaPort()));
        props.put("mail." + protocol + ".connectiontimeout", String.valueOf(Math.max(1, smtp.getConnectionTimeoutMs())));
        props.put("mail." + protocol + ".timeout", String.valueOf(Math.max(1, smtp.getReadTimeoutMs())));
        props.put("mail." + protocol + ".writetimeout", String.valueOf(Math.max(1, smtp.getWriteTimeoutMs())));
        props.put("mail." + protocol + ".localhost", mta.getEhloHost());
        props.put("mail." + protocol + ".auth", String.valueOf(smtp.getUsername() != null && !smtp.getUsername().isBlank()));
        props.put("mail." + protocol + ".starttls.enable", String.valueOf(mta.getStarttls().isEnabled()));
        props.put("mail." + protocol + ".starttls.required", String.valueOf(mta.getStarttls().isRequired()));
        props.put("mail." + protocol + ".ssl.enable", String.valueOf(mta.getSsl().isEnabled()));
        props.put("mail." + protocol + ".from", sanitizeAddress(envelopeFrom));
        if (mta.getStarttls().isEnabled() || mta.getSsl().isEnabled()) {
            props.put("mail." + protocol + ".ssl.checkserveridentity", "true");
        }
        return props;
    }

    private void logResult(MtaResult result, Instant started, SmtpEnvelope envelope) {
        long durationMs = Duration.between(started, Instant.now()).toMillis();
        String domain = destinationDomain(envelope);
        log.info(
                "MTA submit implementation={} outcome={} retryable={} smtpCode={} durationMs={} destinationDomain={}",
                implementation(),
                result.outcome(),
                result.retryable(),
                result.smtpCode(),
                durationMs,
                domain
        );
    }

    private static String destinationDomain(SmtpEnvelope envelope) {
        if (envelope.recipients().isEmpty()) {
            return null;
        }
        String first = envelope.recipients().getFirst();
        int at = first.lastIndexOf('@');
        if (at < 0) {
            return SensitiveDataMasker.maskValue(first);
        }
        return first.substring(at + 1).toLowerCase(Locale.ROOT);
    }

    private static String sanitizeAddress(String address) {
        return address.replaceAll("[\\r\\n<>]", "").trim();
    }

    private static void closeQuietly(SMTPTransport transport) {
        if (transport == null) {
            return;
        }
        try {
            if (transport.isConnected()) {
                transport.close();
            }
        } catch (Exception ignored) {
            // Best-effort close; submission result already captured.
        }
    }
}
