#!/usr/bin/env bash
# Run from the uploaded project on EC2; the SDK uses its attached instance role.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then
  set -a
  source <(sed 's/\r$//' .env)
  set +a
fi
# Keep database, queue, Redis and security settings from the existing environment.
export SPRING_PROFILES_ACTIVE="${SES_APP_PROFILE:-${SPRING_PROFILES_ACTIVE:-local}}"
export MTA_IMPLEMENTATION=ses
export SES_ENABLED=true
export SES_REGION="${SES_REGION:-ap-south-1}"
export SES_CONFIGURATION_SET_NAME="${SES_CONFIGURATION_SET_NAME:-my-first-configuration-set}"
export EMAIL_PUBLIC_DELIVERY_ENABLED=true
export ALLOW_PLATFORM_TEST_SENDERS=false
export BOUNCE_DOMAIN="${BOUNCE_DOMAIN:-bounce.texto-qa.in}"
printf 'Starting backend with real Amazon SES delivery in %s.\n' "$SES_REGION"
cd backend
exec bash ./mvnw spring-boot:run "$@"
