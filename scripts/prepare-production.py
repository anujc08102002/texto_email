"""Generate a fresh single-host deployment configuration; never overwrite existing secrets."""
import argparse
import base64
import os
from pathlib import Path
import re
import secrets


def generate(output, environment, admin_email):
    if not re.fullmatch(r"[A-Za-z0-9._+%-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", admin_email):
        raise ValueError("Provide an admin email without spaces or environment-file delimiters")
    zone = "texto.in" if environment == "production" else "texto-qa.in"
    web, api = "email." + zone, "email-api." + zone
    database_password, rabbit_password, redis_password = [secrets.token_hex(24) for _ in range(3)]
    shared = {
        "DATABASE_USERNAME": "texto_email", "DATABASE_PASSWORD": database_password,
        "RABBITMQ_USERNAME": "texto_email", "RABBITMQ_PASSWORD": rabbit_password,
        "REDIS_PASSWORD": redis_password,
    }
    backend = {
        "SPRING_PROFILES_ACTIVE": "prod", "SERVER_ADDRESS": "127.0.0.1", "SERVER_PORT": "18080",
        "SERVER_FORWARD_HEADERS_STRATEGY": "framework",
        "DATABASE_URL": "jdbc:postgresql://127.0.0.1:15433/texto_email", **shared,
        "RABBITMQ_HOST": "127.0.0.1", "RABBITMQ_PORT": "15673", "REDIS_HOST": "127.0.0.1", "REDIS_PORT": "16379",
        "MTA_IMPLEMENTATION": "ses", "MTA_HOSTNAME": api, "SES_ENABLED": "true", "SES_REGION": "ap-south-1",
        "SES_CONFIGURATION_SET_NAME": "my-first-configuration-set", "EMAIL_PUBLIC_DELIVERY_ENABLED": "false",
        "SES_FEEDBACK_ENABLED": "false", "SES_FEEDBACK_QUEUE_URL": "",
        "EMAIL_PUBLIC_BASE_URL": "https://" + api, "CORS_ALLOWED_ORIGINS": "https://" + web,
        "ALLOW_PLATFORM_TEST_SENDERS": "false", "API_DOCS_PUBLIC": "false",
        "BOUNCE_DOMAIN": "bounce.notify." + zone, "SPF_INCLUDE": "amazonses.com", "SPF_INCLUDE_PROVISIONED": "true",
        "SPF_AUTHORIZE_OUTBOUND_IP": "false", "DKIM_SELECTOR": "texto", "DMARC_POLICY": "none",
        "DKIM_KEY_ENCRYPTION_KEY": base64.b64encode(secrets.token_bytes(32)).decode(),
        "WEBHOOKS_ENCRYPTION_KEY": base64.b64encode(secrets.token_bytes(32)).decode(),
        "EMAIL_ATTACHMENT_STORAGE_ENABLED": "true", "EMAIL_ATTACHMENT_ROOT": "/var/lib/texto-email/attachments",
        "EMAIL_RATE_LIMIT_PER_MINUTE": "120", "EMAIL_MAX_RFC822_BYTES": "10485760",
        "BILLING_PROVIDER": "noop", "OPS_BOOTSTRAP_EMAIL": admin_email,
        "OPS_BOOTSTRAP_PASSWORD": secrets.token_urlsafe(30),
    }
    frontend = {"NEXT_PUBLIC_API_BASE_URL": "https://" + api, "API_BASE_URL": "http://127.0.0.1:18080",
                "NEXT_PUBLIC_WHATSAPP_BASE_URL": "https://texto.in"}
    nginx = r"""# HTTP bootstrap. Certbot adds HTTPS after public DNS points here.
server {
    listen 80;
    server_name WEB_HOST;
    client_max_body_size 8m;
    location / {
        proxy_pass http://127.0.0.1:13000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;
    }
}
server {
    listen 80;
    server_name API_HOST;
    client_max_body_size 8m;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    location ^~ /api/v1/public/unsubscribe/ {
        access_log off;
        error_log /var/log/nginx/texto-unsubscribe-error.log crit;
        proxy_pass http://127.0.0.1:18080;
    }
    location /api/ { proxy_pass http://127.0.0.1:18080; }
    location = /actuator/health { proxy_pass http://127.0.0.1:18080; }
    location / { return 404; }
}
""".replace("WEB_HOST", web).replace("API_HOST", api)
    files = {
        "backend.env": "# Never commit. Enable sending/feedback only after SES checks.\n" + "".join(f"{k}={v}\n" for k, v in backend.items()),
        "frontend.env": "".join(f"{k}={v}\n" for k, v in frontend.items()),
        "dependencies.env": "DATABASE_NAME=texto_email\n" + "".join(f"{k}={v}\n" for k, v in shared.items()),
        "nginx.conf": nginx,
    }
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    if any((output / name).exists() for name in files):
        raise FileExistsError("Existing configuration found; refusing to replace credentials. Edit it in place.")
    for name, content in files.items():
        with (output / name).open("x", encoding="utf-8", newline="\n") as file:
            os.chmod(file.name, 0o600)
            file.write(content)
    print(f"Created configuration for {environment}: {web} and {api}")
    print("No credentials printed. Store encrypted backups of the environment files.")
    print("SES sending and feedback are initially disabled; follow docs/production-deployment.md.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--environment", choices=["staging", "production"], required=True)
    parser.add_argument("--admin-email", required=True)
    parser.add_argument("--output-dir", default="/etc/texto-email")
    args = parser.parse_args()
    generate(args.output_dir, args.environment, args.admin_email)
