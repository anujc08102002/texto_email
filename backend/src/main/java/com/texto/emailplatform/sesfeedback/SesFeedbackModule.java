package com.texto.emailplatform.sesfeedback;

/**
 * SES feedback ingestion module. Consumes bounce/complaint events from SQS,
 * routes them to existing bounce/complaint processing pipelines.
 * Configured via email-platform.ses-feedback.enabled.
 */
public final class SesFeedbackModule {

    private SesFeedbackModule() {
    }
}
