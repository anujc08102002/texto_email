FROM debian:bookworm-slim

ENV DEBIAN_FRONTEND=noninteractive

RUN echo "postfix postfix/mailname string mail.texto.test" | debconf-set-selections \
    && echo "postfix postfix/main_mailer_type string Internet Site" | debconf-set-selections \
    && apt-get update \
    && apt-get install -y --no-install-recommends postfix ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY main.cf /etc/postfix/main.cf
COPY docker-entrypoint.sh /docker-entrypoint.sh

RUN chmod +x /docker-entrypoint.sh \
    && mkdir -p /var/mail/texto.test/new /var/mail/texto.test/cur /var/mail/texto.test/tmp

EXPOSE 25

ENTRYPOINT ["/docker-entrypoint.sh"]
