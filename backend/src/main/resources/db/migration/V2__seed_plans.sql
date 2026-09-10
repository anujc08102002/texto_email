INSERT INTO plans (id, code, name, description, active, created_at, updated_at)
VALUES
    (
        '11111111-1111-1111-1111-111111111111',
        'starter',
        'Starter',
        'Transactional email for early-stage products.',
        TRUE,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00'
    ),
    (
        '22222222-2222-2222-2222-222222222222',
        'growth',
        'Growth',
        'Higher sending volume with analytics and domains.',
        TRUE,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00'
    ),
    (
        '33333333-3333-3333-3333-333333333333',
        'scale',
        'Scale',
        'Production sending with dedicated capacity and entitlements.',
        TRUE,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00'
    );

INSERT INTO entitlements (id, plan_id, feature_key, limit_value)
VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1', '11111111-1111-1111-1111-111111111111', 'monthly_email_sends', 10000),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2', '22222222-2222-2222-2222-222222222222', 'monthly_email_sends', 100000),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3', '33333333-3333-3333-3333-333333333333', 'monthly_email_sends', 1000000);
