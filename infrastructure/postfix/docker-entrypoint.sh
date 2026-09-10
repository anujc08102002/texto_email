#!/bin/sh
set -eu

POSTFIX_UID="$(id -u postfix)"
POSTFIX_GID="$(id -g postfix)"

mkdir -p /var/mail/texto.test/new /var/mail/texto.test/cur /var/mail/texto.test/tmp
chown -R "${POSTFIX_UID}:${POSTFIX_GID}" /var/mail

postconf -e "virtual_uid_maps = static:${POSTFIX_UID}"
postconf -e "virtual_gid_maps = static:${POSTFIX_GID}"
postconf -e "virtual_minimum_uid = ${POSTFIX_UID}"
postconf -F '*/*/chroot = n'

postfix check
exec /usr/sbin/postfix start-fg
