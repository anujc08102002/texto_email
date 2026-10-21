# Connect the existing Amazon SES setup

Production will use the existing SES v2 transport. No SMTP replacement, AWS provisioning, credential rotation, or subscriber send was performed.

## Confirmed setup

The user confirmed the following deployment values on 19 October 2026:

- SES region: `ap-south-1`; existing configuration set: `my-first-configuration-set` (verified by the user with `aws sesv2 list-configuration-sets`). Local `.env` was updated to this confirmed name.
- EC2 instance: `texto-mta-01`, instance ID `i-0d7d426de68c2cfbe`, public IP `3.109.194.88`.
- Instance-profile role: `TextoEmailProductionRole`, AWS account `172159866090`. The user verified role authentication with `aws sts get-caller-identity`.
- SES service endpoint: `https://email.ap-south-1.amazonaws.com`. This is AWS's sending API, not this application's public API origin. Do not use it for `EMAIL_PUBLIC_BASE_URL`.
- Local bounce domain: `bounce.texto-qa.in`.
- This workstation has no AWS credentials or profile. The backend uses the SDK default credential chain to obtain the existing EC2 instance-role credentials on deployment.
- The application's public HTTPS API domain and SSH/SSM access method are still needed. The live host and SES account have not been independently checked by this agent.

Use the existing instance role or deployment secret store. Do not put AWS keys in source control or chat.

## Deployment values

```dotenv
MTA_IMPLEMENTATION=ses
SES_ENABLED=true
SES_REGION=ap-south-1
SES_CONFIGURATION_SET_NAME=my-first-configuration-set
SES_FEEDBACK_ENABLED=true
SES_FEEDBACK_QUEUE_URL=<actual-existing-feedback-queue-url>
EMAIL_PUBLIC_BASE_URL=https://<actual-api-domain>
CORS_ALLOWED_ORIGINS=https://<actual-dashboard-domain>
ALLOW_PLATFORM_TEST_SENDERS=false
```

Preserve existing database, RabbitMQ, Redis, DKIM custody and sender-domain settings. Configure `EMAIL_PUBLIC_DELIVERY_ENABLED` deliberately after verification. This work does not modify the existing local environment's public-delivery switch.

## Read-only verification

Open Admin / System / Amazon SES readiness, enter the real verified sending identity, and select Check SES. It uses the deployment's existing AWS credentials without sending email or provisioning resources.

Equivalent platform-admin endpoint:

```text
GET /api/v1/admin/delivery/ses/readiness?identity=<sending-domain>
Authorization: Bearer <platform-admin-session>
```

It checks regional account sending/production access, configuration-set existence and sending options, enabled bounce/complaint event destinations, verified identity and DKIM, and daily/per-second quotas. Provider errors return sanitized codes without credential diagnostics.

The diagnostic requires `ses:GetAccount`, `ses:GetConfigurationSet`, `ses:GetConfigurationSetEventDestinations`, and `ses:GetEmailIdentity`, in addition to existing send/feedback permissions. Missing diagnostic permission is not a recipient-delivery failure.

`PROVIDER_CHECKS_PASSED` verifies these SES API checks only. SNS/SQS policies, actual feedback consumption, received-message signatures, public unsubscribe routing, and inbox placement still require deployment checks against owned mailboxes.

SES authorization/configuration/identity failures now mark messages failed instead of falsely bouncing and suppressing healthy recipients. Authoritative SES bounces/complaints continue through the existing feedback pipeline.

Sources: [SES account status and quotas](https://docs.aws.amazon.com/ses/latest/APIReference-V2/API_GetAccount.html), [configuration sets](https://docs.aws.amazon.com/ses/latest/APIReference-V2/API_CreateConfigurationSet.html), [event destinations](https://docs.aws.amazon.com/ses/latest/APIReference-V2/API_GetConfigurationSetEventDestinations.html).


## Send using SES before the public server domain is final (21 October 2026)

The SES API transport is real delivery. It does not require a public SMTP server, public IP, or the application's final HTTPS domain. EC2 supplies AWS credentials through its instance role. This workstation currently has no AWS CLI installation, credential file, profile config, or AWS credential environment variables; copying application files cannot transfer instance-role access.

On the existing EC2 host, retain the working database, RabbitMQ, Redis and security configuration in `.env`, then start the backend with:

```bash
bash scripts/start-ses.sh
```

The helper uses SES in `ap-south-1`, configuration set `my-first-configuration-set`, and the SDK default credential chain. It preserves the configured Spring profile. It never sends a message during startup. Start the frontend separately with `cd frontend && npm ci && npm run build && npm start`. Set its API origin to the backend's reachable origin before building. Existing running processes must be restarted to load these changes.

For this Windows workstation, once AWS authentication is available:

```powershell
.\scripts\start-local.ps1 -Delivery ses
# Or use an already-configured named profile:
.\scripts\start-local.ps1 -Delivery ses -AwsProfile texto
```

The Windows helper keeps the local database/queue setup and selects real SES instead of Mailpit. Both SES launch paths disable fabricated `*.texto.test` platform senders. Use a tenant-authorized, verified domain that is also verified in SES in the selected region. API sends require a LIVE key; TEST keys remain blocked from SES. Use **Send email** or the campaign test-send control for a chosen real recipient. Inspect the resulting message detail: an SES message ID means provider acceptance, not confirmed inbox delivery.

If the SES account is in the sandbox, real recipient addresses also need SES verification; production access removes that recipient restriction. See [AWS sending documentation](https://docs.aws.amazon.com/ses/latest/APIReference-V2/API_SendEmail.html). The admin System page includes read-only SES readiness checks for account access, sender identity and configuration set.

A localhost origin is sufficient for a simple delivery test. Campaign unsubscribe links require an origin reachable by the recipients; use the final public URL before customer campaigns. Feedback processing continues to require the existing SNS/SQS configuration. No live email was sent or EC2 deployment performed during this change because this session has neither AWS credentials nor remote host access.
