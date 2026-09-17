#!/bin/sh
set -eu

# Public Internet MX delivery is OFF unless BOTH:
#   POSTFIX_ENABLE_PUBLIC_DELIVERY=yes
#   POSTFIX_PUBLIC_DELIVERY_CONFIRM=ENABLE_PUBLIC_MX_DELIVERY
# Local compose hard-codes "no". Do not treat "true"/"1" as enabled.
PUBLIC_DELIVERY="${POSTFIX_ENABLE_PUBLIC_DELIVERY:-no}"
case "${PUBLIC_DELIVERY}" in
  no|false|"")
    ;;
  yes)
    if [ "${POSTFIX_PUBLIC_DELIVERY_CONFIRM:-}" != "ENABLE_PUBLIC_MX_DELIVERY" ]; then
      echo "POSTFIX_ENABLE_PUBLIC_DELIVERY=yes requires POSTFIX_PUBLIC_DELIVERY_CONFIRM=ENABLE_PUBLIC_MX_DELIVERY" >&2
      echo "Public Internet delivery remains disabled." >&2
      exit 1
    fi
    ;;
  *)
    echo "POSTFIX_ENABLE_PUBLIC_DELIVERY must be 'no' or 'yes' (got '${PUBLIC_DELIVERY}')." >&2
    exit 1
    ;;
esac

reject_world_mynetworks() {
  case "${POSTFIX_MYNETWORKS:-}" in
    *0.0.0.0/0*|*"::/0"*)
      echo "POSTFIX_MYNETWORKS must not include 0.0.0.0/0 or ::/0 (open relay)." >&2
      exit 1
      ;;
  esac
}
reject_world_mynetworks

POSTFIX_UID="$(id -u postfix)"
POSTFIX_GID="$(id -g postfix)"

mkdir -p /var/mail/texto.test/new /var/mail/texto.test/cur /var/mail/texto.test/tmp
mkdir -p /var/mail/bounce/new /var/mail/bounce/cur /var/mail/bounce/tmp
chown -R "${POSTFIX_UID}:${POSTFIX_GID}" /var/mail

BOUNCE_DOMAIN="${POSTFIX_BOUNCE_DOMAIN:-bounce.texto.test}"
BOUNCE_RE="$(printf '%s' "${BOUNCE_DOMAIN}" | sed 's/\./\\./g')"
printf '%s\n' \
  "/@${BOUNCE_RE}$/    bounce/" \
  '/@texto\.test$/            texto.test/' \
  > /etc/postfix/virtual_mailbox_maps.regexp
printf '%s\n' \
  "/^bounce\\+[A-Fa-f0-9]{32}@${BOUNCE_RE}$/  OK" \
  "/@${BOUNCE_RE}$/                          REJECT unexpected bounce address" \
  '/@texto\.test$/                                  DUNNO' \
  > /etc/postfix/recipient_access.regexp
postconf -e "virtual_mailbox_domains = texto.test ${BOUNCE_DOMAIN}"

postconf -e "virtual_uid_maps = static:${POSTFIX_UID}"
postconf -e "virtual_gid_maps = static:${POSTFIX_GID}"
postconf -e "virtual_minimum_uid = ${POSTFIX_UID}"
postconf -F '*/*/chroot = n'

# Hostname / EHLO identity — environment, not the container hostname.
if [ -n "${POSTFIX_MYHOSTNAME:-}" ]; then
  postconf -e "myhostname = ${POSTFIX_MYHOSTNAME}"
fi
if [ -n "${POSTFIX_MYDOMAIN:-}" ]; then
  postconf -e "mydomain = ${POSTFIX_MYDOMAIN}"
fi
if [ -n "${POSTFIX_MYORIGIN:-}" ]; then
  postconf -e "myorigin = ${POSTFIX_MYORIGIN}"
fi
if [ -n "${POSTFIX_SMTP_HELO_NAME:-}" ]; then
  postconf -e "smtp_helo_name = ${POSTFIX_SMTP_HELO_NAME}"
fi
if [ -n "${POSTFIX_MYNETWORKS:-}" ]; then
  postconf -e "mynetworks = ${POSTFIX_MYNETWORKS}"
fi

# TLS overlay. smtpd_* = application → Postfix. smtp_* = Postfix → MX.
SMTPD_TLS_LEVEL="${POSTFIX_SMTPD_TLS_SECURITY_LEVEL:-none}"
SMTP_TLS_LEVEL="${POSTFIX_SMTP_TLS_SECURITY_LEVEL:-none}"
postconf -e "smtpd_tls_security_level = ${SMTPD_TLS_LEVEL}"
postconf -e "smtp_tls_security_level = ${SMTP_TLS_LEVEL}"

if [ "${SMTPD_TLS_LEVEL}" != "none" ]; then
  if [ -z "${POSTFIX_SMTPD_TLS_CERT_FILE:-}" ] || [ -z "${POSTFIX_SMTPD_TLS_KEY_FILE:-}" ]; then
    echo "smtpd TLS level '${SMTPD_TLS_LEVEL}' requires POSTFIX_SMTPD_TLS_CERT_FILE and POSTFIX_SMTPD_TLS_KEY_FILE" >&2
    exit 1
  fi
  if [ ! -f "${POSTFIX_SMTPD_TLS_CERT_FILE}" ] || [ ! -f "${POSTFIX_SMTPD_TLS_KEY_FILE}" ]; then
    echo "smtpd TLS certificate or key file is missing" >&2
    exit 1
  fi
  postconf -e "smtpd_tls_cert_file = ${POSTFIX_SMTPD_TLS_CERT_FILE}"
  postconf -e "smtpd_tls_key_file = ${POSTFIX_SMTPD_TLS_KEY_FILE}"
  postconf -e "smtpd_tls_loglevel = 1"
fi

if [ "${SMTP_TLS_LEVEL}" != "none" ]; then
  postconf -e "smtp_tls_loglevel = 1"
  if [ -n "${POSTFIX_SMTP_TLS_CAFILE:-}" ]; then
    postconf -e "smtp_tls_CAfile = ${POSTFIX_SMTP_TLS_CAFILE}"
  fi
fi

  if [ "${PUBLIC_DELIVERY}" = "yes" ]; then
  if [ -z "${POSTFIX_MYHOSTNAME:-}" ]; then
    echo "POSTFIX_MYHOSTNAME is required when public delivery is enabled." >&2
    exit 1
  fi
  HELO_NAME="${POSTFIX_SMTP_HELO_NAME:-${POSTFIX_MYHOSTNAME}}"
  if [ -n "${POSTFIX_SMTP_HELO_NAME:-}" ] && [ "${POSTFIX_SMTP_HELO_NAME}" != "${POSTFIX_MYHOSTNAME}" ]; then
    echo "POSTFIX_SMTP_HELO_NAME must match POSTFIX_MYHOSTNAME for production identity." >&2
    exit 1
  fi
  case "${POSTFIX_MYHOSTNAME}" in
    localhost|*.localhost|*.local|*.test|texto.local)
      echo "POSTFIX_MYHOSTNAME must be a real production FQDN (not localhost, *.local, or *.test)." >&2
      exit 1
      ;;
  esac
  postconf -e "smtp_helo_name = ${HELO_NAME}"
  # Trusted application subnet may relay to recipient MX. Unauthenticated clients
  # remain blocked by smtpd_client_restrictions = permit_mynetworks, reject.
  # Do not add permit_all. Do not set mynetworks to 0.0.0.0/0.
  postconf -e "default_transport = smtp"
  postconf -e "relay_transport = smtp"
  postconf -e "smtpd_relay_restrictions = permit_mynetworks, reject_unauth_destination"
  postconf -e "smtpd_recipient_restrictions = check_recipient_access regexp:/etc/postfix/recipient_access.regexp, permit_mynetworks, reject_unauth_destination"
  if [ "${SMTP_TLS_LEVEL}" = "none" ]; then
    postconf -e "smtp_tls_security_level = may"
    postconf -e "smtp_tls_loglevel = 1"
  fi
  if [ -n "${POSTFIX_SMTP_BIND_ADDRESS:-}" ]; then
    postconf -e "smtp_bind_address = ${POSTFIX_SMTP_BIND_ADDRESS}"
  fi
  postconf -e "smtp_destination_concurrency_limit = ${POSTFIX_SMTP_DESTINATION_CONCURRENCY_LIMIT:-10}"
  postconf -e "smtp_destination_recipient_limit = ${POSTFIX_SMTP_DESTINATION_RECIPIENT_LIMIT:-50}"
fi

postfix check
exec /usr/sbin/postfix start-fg
