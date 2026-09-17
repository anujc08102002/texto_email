FROM debian:bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive

RUN echo "postfix postfix/mailname string mail.texto.test" | debconf-set-selections \
    && echo "postfix postfix/main_mailer_type string Internet Site" | debconf-set-selections \
    && apt-get update \
    && apt-get install -y --no-install-recommends postfix ca-certificates netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*

COPY main.cf /etc/postfix/main.cf
COPY virtual_mailbox_maps.regexp /etc/postfix/virtual_mailbox_maps.regexp
COPY recipient_access.regexp /etc/postfix/recipient_access.regexp
COPY docker-entrypoint.sh /docker-entrypoint.sh
COPY smtp-ping.sh /smtp-ping.sh

RUN chmod +x /docker-entrypoint.sh /smtp-ping.sh \
    && mkdir -p /var/mail/texto.test/new /var/mail/texto.test/cur /var/mail/texto.test/tmp \
    && mkdir -p /var/mail/bounce/new /var/mail/bounce/cur /var/mail/bounce/tmp

EXPOSE 25

HEALTHCHECK --interval=10s --timeout=5s --retries=10 CMD /smtp-ping.sh

ENTRYPOINT ["/docker-entrypoint.sh"]
