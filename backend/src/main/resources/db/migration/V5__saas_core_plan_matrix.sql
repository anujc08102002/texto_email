-- Seed plan feature matrix and plan limits (database-driven; not Java conditionals)

-- FREE features
INSERT INTO plan_features (id, plan_id, feature_id, enabled)
SELECT gen_random_uuid(), p.id, f.id, TRUE
FROM plans p
CROSS JOIN features f
WHERE p.code = 'FREE'
  AND f.code IN ('API_SENDING', 'CUSTOM_DOMAIN', 'TEMPLATES', 'BASIC_ANALYTICS', 'SUPPRESSION', 'BOUNCE_HANDLING');

INSERT INTO plan_features (id, plan_id, feature_id, enabled)
SELECT gen_random_uuid(), p.id, f.id, FALSE
FROM plans p
CROSS JOIN features f
WHERE p.code = 'FREE'
  AND f.code NOT IN ('API_SENDING', 'CUSTOM_DOMAIN', 'TEMPLATES', 'BASIC_ANALYTICS', 'SUPPRESSION', 'BOUNCE_HANDLING');

-- STARTER features
INSERT INTO plan_features (id, plan_id, feature_id, enabled)
SELECT gen_random_uuid(), p.id, f.id, TRUE
FROM plans p
CROSS JOIN features f
WHERE p.code = 'STARTER'
  AND f.code IN ('API_SENDING', 'SMTP_SENDING', 'CUSTOM_DOMAIN', 'TEMPLATES', 'WEBHOOKS', 'BASIC_ANALYTICS', 'SUPPRESSION', 'BOUNCE_HANDLING');

INSERT INTO plan_features (id, plan_id, feature_id, enabled)
SELECT gen_random_uuid(), p.id, f.id, FALSE
FROM plans p
CROSS JOIN features f
WHERE p.code = 'STARTER'
  AND f.code NOT IN ('API_SENDING', 'SMTP_SENDING', 'CUSTOM_DOMAIN', 'TEMPLATES', 'WEBHOOKS', 'BASIC_ANALYTICS', 'SUPPRESSION', 'BOUNCE_HANDLING');

-- BUSINESS features
INSERT INTO plan_features (id, plan_id, feature_id, enabled)
SELECT gen_random_uuid(), p.id, f.id, TRUE
FROM plans p
CROSS JOIN features f
WHERE p.code = 'BUSINESS'
  AND f.code IN (
      'API_SENDING', 'SMTP_SENDING', 'CUSTOM_DOMAIN', 'TEMPLATES', 'WEBHOOKS',
      'BASIC_ANALYTICS', 'ADVANCED_ANALYTICS', 'SUPPRESSION', 'BOUNCE_HANDLING',
      'CAMPAIGNS', 'SEGMENTATION', 'CUSTOM_TRACKING_DOMAIN'
  );

INSERT INTO plan_features (id, plan_id, feature_id, enabled)
SELECT gen_random_uuid(), p.id, f.id, FALSE
FROM plans p
CROSS JOIN features f
WHERE p.code = 'BUSINESS'
  AND f.code IN ('DEDICATED_IP', 'IP_POOLS', 'REPUTATION_DASHBOARD');

-- ENTERPRISE: all features enabled
INSERT INTO plan_features (id, plan_id, feature_id, enabled)
SELECT gen_random_uuid(), p.id, f.id, TRUE
FROM plans p
CROSS JOIN features f
WHERE p.code = 'ENTERPRISE';

-- Limits (NULL = unlimited)
INSERT INTO plan_limits (id, plan_id, metric, limit_value)
VALUES
    -- FREE
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000001', 'MONTHLY_EMAILS', 500),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000001', 'DOMAINS', 1),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000001', 'USERS', 1),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000001', 'API_KEYS', 2),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000001', 'TEMPLATES', 5),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000001', 'CAMPAIGNS', 0),
    -- STARTER
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000002', 'MONTHLY_EMAILS', 5000),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000002', 'DOMAINS', 3),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000002', 'USERS', 5),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000002', 'API_KEYS', 5),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000002', 'TEMPLATES', 25),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000002', 'CAMPAIGNS', 0),
    -- BUSINESS
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000003', 'MONTHLY_EMAILS', 50000),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000003', 'DOMAINS', 10),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000003', 'USERS', 20),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000003', 'API_KEYS', 20),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000003', 'TEMPLATES', NULL),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000003', 'CAMPAIGNS', NULL),
    -- ENTERPRISE
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000004', 'MONTHLY_EMAILS', NULL),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000004', 'DOMAINS', NULL),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000004', 'USERS', NULL),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000004', 'API_KEYS', NULL),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000004', 'TEMPLATES', NULL),
    (gen_random_uuid(), 'a1000000-0000-4000-8000-000000000004', 'CAMPAIGNS', NULL);
