-- Track the created_at of the most recently applied billing webhook per subscription so the
-- webhook handler can ignore out-of-order deliveries. Razorpay delivers webhooks at-least-once
-- and does not guarantee ordering (https://razorpay.com/docs/webhooks/validate-test/).
ALTER TABLE subscriptions
    ADD COLUMN last_billing_event_at timestamptz;
