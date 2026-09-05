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
  EVENTS_ENABLED: ${EVENTS_ENABLED:-true}
  JWT_SECRET: ${JWT_SECRET:?JWT_SECRET must be set}
  JWT_ISSUER: ${JWT_ISSUER:-smartseason-identity}
  LOG_LEVEL: ${LOG_LEVEL:-INFO}

services:
  postgres:
    image: postgres:16-alpine
    restart: unless-stopped
    logging: *default-logging
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-postgres}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set}
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

  redis:
    image: redis:7-alpine
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
    restart: unless-stopped
    logging: *default-logging
    profiles: ["observability"]
    volumes:
      - ./infra/observability/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    ports:
      - "${PROMETHEUS_PORT:-9090}:9090"

  grafana:
    image: grafana/grafana:11.3.1
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
    restart: unless-stopped
    logging: *default-logging
    environment:
      GATEWAY_URL: http://api-gateway:8080
      NODE_ENV: production
    ports:
      - "${WEB_PORT:-3000}:3000"
    depends_on:
      - api-gateway
"""

SERVICE_TEMPLATE = """
  {name}:
    build: ./services/{name}
    restart: unless-stopped
    logging: *default-logging
    profiles: ["{profile}", "all"]
    environment:
      <<: *service-env
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/{db}
    depends_on:
      postgres:
        condition: service_healthy
      redpanda:
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
