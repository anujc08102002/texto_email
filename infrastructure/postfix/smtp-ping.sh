#!/bin/sh
set -eu
# Health probe: Postfix is running and the SMTP listener returns RFC 5321 220.
# Never send MAIL FROM / RCPT TO / DATA.
/usr/sbin/postfix status >/dev/null
reply="$(printf 'QUIT\r\n' | /usr/bin/nc -w 3 127.0.0.1 25 | tr -d '\r' | head -n 1 || true)"
case "${reply}" in
  220*) exit 0 ;;
  *)
    echo "SMTP greeting failed: ${reply}" >&2
    exit 1
    ;;
esac
