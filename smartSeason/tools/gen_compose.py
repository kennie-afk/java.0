#!/usr/bin/env python3
"""Emits docker-compose.yml, the Postgres init script and .env.example."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalogue import SERVICES

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

GROUP_PROFILE = {
    "identity": "core", "farm": "core", "workforce": "workforce",
    "iot": "iot", "market": "market", "money": "money", "platform": "platform",
}

HEADER = """name: smartseason

x-logging: &default-logging
  driver: json-file
  options:
    max-size: "10m"
    max-file: "3"

x-service-env: &service-env
  SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-postgres}
  SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set}
  KAFKA_BOOTSTRAP_SERVERS: redpanda:9092
  # Rate-limit counters. Shared, so every replica of a service spends one
  # allowance per caller instead of one each.
  REDIS_HOST: ${REDIS_HOST:-redis}
  REDIS_PORT: ${REDIS_PORT:-6379}
  RATE_LIMIT_ENABLED: ${RATE_LIMIT_ENABLED:-true}
  EVENTS_ENABLED: ${EVENTS_ENABLED:-true}
  JWT_SECRET: ${JWT_SECRET:?JWT_SECRET must be set}
  JWT_ISSUER: ${JWT_ISSUER:-smartseason-identity}
  LOG_LEVEL: ${LOG_LEVEL:-INFO}
  # Application-side pool. These are PgBouncer connections, which are cheap, so
  # this can be generous; what reaches Postgres is capped by default_pool_size
  # in pgbouncer.ini. Raise the two together, never one alone.
  DB_POOL_MAX: ${DB_POOL_MAX:-15}
  # Returns the password-reset code in the response so the flow can be walked
  # without a mail provider. Local demo only - it hands the code to anyone who
  # can name an email address.
  SMARTSEASON_AUTH_EXPOSE_RESET_CODE: ${EXPOSE_RESET_CODE:-false}
  # Advisory model. With no key the agronomy service answers from its built-in
  # rules and says so; with one it reads photographs. Set it in .env or, in a
  # real deployment, from a secret - never in this file.
  ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY:-}
  SMARTSEASON_AI_MODEL: ${SMARTSEASON_AI_MODEL:-claude-sonnet-5}

services:
  postgres:
    image: postgres:16-alpine
    mem_limit: 768m
    mem_reservation: 512m
    restart: unless-stopped
    logging: *default-logging
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-postgres}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set}
    # 27 services each hold a small pool; the 100-connection default is not enough.
    command: ["postgres", "-c", "max_connections=200"]
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./infra/docker/postgres/init-databases.sh:/docker-entrypoint-initdb.d/init-databases.sh:ro
    ports:
      - "${POSTGRES_PORT:-5432}:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER:-postgres}"]
      interval: 10s
      timeout: 5s
      retries: 10

  pgbouncer:
    image: edoburu/pgbouncer:v1.23.1-p2
    mem_limit: 128m
    mem_reservation: 64m
    restart: unless-stopped
    logging: *default-logging
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-postgres}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set}
    entrypoint: ["/bin/sh", "/entrypoint.sh"]
    volumes:
      - ./infra/docker/pgbouncer/pgbouncer.ini:/etc/pgbouncer/pgbouncer.ini:ro
      - ./infra/docker/pgbouncer/entrypoint.sh:/entrypoint.sh:ro
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      # Queries a real database through the pooler rather than the admin console,
      # so the check proves the whole path works, not just that a port is open.
      test: ["CMD-SHELL", "PGPASSWORD=$$POSTGRES_PASSWORD psql -h 127.0.0.1 -p 6432 -U $$POSTGRES_USER -d identity_db -tAc 'SELECT 1' >/dev/null"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 10s

  redis:
    image: redis:7-alpine
    mem_limit: 256m
    mem_reservation: 128m
    restart: unless-stopped
    logging: *default-logging
    command: ["redis-server", "--appendonly", "yes"]
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  redpanda:
    image: redpandadata/redpanda:v24.2.8
    mem_limit: 1g
    mem_reservation: 512m
    restart: unless-stopped
    logging: *default-logging
    command:
      - redpanda
      - start
      - --smp=1
      - --overprovisioned
      - --node-id=0
      - --kafka-addr=PLAINTEXT://0.0.0.0:9092,EXTERNAL://0.0.0.0:19092
      - --advertise-kafka-addr=PLAINTEXT://redpanda:9092,EXTERNAL://localhost:19092
    ports:
      - "${REDPANDA_PORT:-19092}:19092"
    volumes:
      - redpanda-data:/var/lib/redpanda/data
    healthcheck:
      test: ["CMD-SHELL", "rpk cluster health | grep -q 'Healthy:.*true'"]
      interval: 15s
      timeout: 5s
      retries: 10

  prometheus:
    image: prom/prometheus:v2.55.1
    mem_limit: 512m
    mem_reservation: 256m
    restart: unless-stopped
    logging: *default-logging
    profiles: ["observability"]
    volumes:
      - ./infra/observability/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    ports:
      - "${PROMETHEUS_PORT:-9090}:9090"

  grafana:
    image: grafana/grafana:11.3.1
    mem_limit: 384m
    mem_reservation: 192m
    restart: unless-stopped
    logging: *default-logging
    profiles: ["observability"]
    environment:
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_PASSWORD:-admin}
      GF_USERS_ALLOW_SIGN_UP: "false"
    ports:
      - "${GRAFANA_PORT:-3001}:3000"
    volumes:
      - grafana-data:/var/lib/grafana

  api-gateway:
    build: ./apps/api-gateway
    mem_limit: 512m
    mem_reservation: 256m
    restart: unless-stopped
    logging: *default-logging
    environment:
      <<: *service-env
      REDIS_HOST: redis
      CORS_ALLOWED_ORIGINS: ${CORS_ALLOWED_ORIGINS:-http://localhost:3000}
    ports:
      - "${GATEWAY_PORT:-8080}:8080"
    depends_on:
      redis:
        condition: service_healthy

  web:
    build: ./apps/web
    mem_limit: 512m
    mem_reservation: 256m
    restart: unless-stopped
    logging: *default-logging
    environment:
      GATEWAY_URL: http://api-gateway:8080
      NODE_ENV: production
      # Lets the sign-in page open the platform as any configured account
      # without asking for a password, so each role can be checked quickly.
      # Off by default; belongs on a build or staging deployment only.
      QUICK_SIGN_IN: ${QUICK_SIGN_IN:-false}
      QUICK_SIGN_IN_PASSWORD: ${QUICK_SIGN_IN_PASSWORD:-}
    ports:
      - "${WEB_PORT:-3000}:3000"
    healthcheck:
      test: ["CMD", "node", "-e", "fetch('http://127.0.0.1:3000/api/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 20s
    depends_on:
      - api-gateway
"""

SERVICE_TEMPLATE = """
  {name}:
    mem_limit: 384m
    mem_reservation: 128m
    # A serial collector and tier-1 compilation keep an idle service near 300MB
    # instead of 350MB, which is what lets all 27 run at once. Metaspace is
    # capped at 160m, not lower: Spring Boot with JPA and Kafka loads about
    # 140MB of classes, and a tighter cap dies on the first write request.
    entrypoint:
      ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=35.0",
       "-XX:+UseSerialGC", "-XX:MaxMetaspaceSize=160m", "-XX:TieredStopAtLevel=1",
       "-Xss512k", "-jar", "app.jar"]
    build: ./services/{name}
    restart: unless-stopped
    logging: *default-logging
    profiles: ["{profile}", "all"]
    environment:
      <<: *service-env
      SPRING_DATASOURCE_URL: jdbc:postgresql://pgbouncer:6432/{db}?prepareThreshold=0
    depends_on:
      pgbouncer:
        condition: service_healthy
      redpanda:
        condition: service_healthy
      redis:
        condition: service_healthy
"""

FOOTER = """
volumes:
  postgres-data:
  redis-data:
  redpanda-data:
  grafana-data:
"""

def compose():
    parts = [HEADER]
    for spec in SERVICES:
        parts.append(SERVICE_TEMPLATE.format(
            name=spec["name"], db=spec["db"],
            profile=GROUP_PROFILE.get(spec["group"], "platform")))
    parts.append(FOOTER)
    return "".join(parts)

def init_script():
    lines = ["#!/bin/sh", "set -e", ""]
    for spec in SERVICES:
        lines.append(f'psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" '
                     f'-c "CREATE DATABASE {spec["db"]};"')
    return "\n".join(lines) + "\n"

def prometheus_config():
    targets = ",\n".join(
        f"          - '{s['name']}:{s['port']}'" for s in SERVICES)
    return f"""global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: smartseason-services
    metrics_path: /actuator/prometheus
    static_configs:
      - targets:
{targets}
          - 'api-gateway:8080'
"""

def env_example():
    return """POSTGRES_USER=postgres
POSTGRES_PASSWORD=change-me-locally
POSTGRES_PORT=5432

JWT_SECRET=replace-with-a-64-character-minimum-random-string-before-any-deploy
JWT_ISSUER=smartseason-identity

EVENTS_ENABLED=true
LOG_LEVEL=INFO

CORS_ALLOWED_ORIGINS=http://localhost:3000
GRAFANA_PASSWORD=admin

GATEWAY_PORT=8080
WEB_PORT=3000
PROMETHEUS_PORT=9090
GRAFANA_PORT=3001
REDPANDA_PORT=19092
"""

def write(path, content, executable=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as handle:
        handle.write(content)
    if executable:
        os.chmod(path, 0o755)

def main():
    write(os.path.join(ROOT, "docker-compose.yml"), compose())
    write(os.path.join(ROOT, "infra/docker/postgres/init-databases.sh"), init_script(), True)
    write(os.path.join(ROOT, "infra/observability/prometheus.yml"), prometheus_config())
    write(os.path.join(ROOT, ".env.example"), env_example())
    print(f"compose written: {len(SERVICES)} services + gateway + web")

if __name__ == "__main__":
    main()
