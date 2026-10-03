#!/usr/bin/env python3
"""Emits the Kubernetes manifests for every service.

Generated rather than hand-written for the same reason the services are: there are 29 of
them, and a hand-maintained set drifts the moment one service gains an environment
variable. The three checks in tools/check_deploy.py exist to catch that drift when it
happens anyway.

Design decisions worth stating, because they are not defaults:

  * Ingress is single-host, path-routed. /api goes to the gateway, everything else to the
    front end, on one origin. Two hostnames would make every API call cross-origin and
    cost a CORS preflight on each mutation, a cookie that needs SameSite=None, and a
    gateway whose allowed origins must track the front end's hostname.

  * Databases are not deployed here. Each service points at a managed Postgres through
    SPRING_DATASOURCE_URL. Running 27 stateful sets to back 27 services is how a cluster
    becomes unmaintainable; the compose file remains the way to run the whole thing on
    one machine.

  * /actuator is reachable inside the cluster for probes and Prometheus and is not routed
    from outside. Publishing it hands an unauthenticated caller the service's
    configuration and dependency graph.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from catalogue import SERVICES
import tpl_data

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "k8s")
NAMESPACE = "smartseason"
HOST = "smartseason.co.ke"

DEPLOYMENT = """---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {name}
  namespace: {ns}
  labels:
    app: {name}
spec:
  replicas: 2
  selector:
    matchLabels:
      app: {name}
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app: {name}
    spec:
      containers:
        - name: {name}
          image: smartseason/{name}:latest
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: {port}
          env:
            # DB_HOST and DB_PORT must be listed before SPRING_DATASOURCE_URL: Kubernetes
            # only expands a $(VAR) reference in a container env value against vars that
            # appear earlier in this same list. Get the order backwards and the pod does
            # not fail to start with a clear error - it starts with the literal string
            # "$(DB_HOST)" as its hostname, which every driver rejects, so at least that
            # part fails loud. Found the hard way: this exact bug shipped for months
            # because it was never applied to a real cluster.
            - name: DB_HOST
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: DB_HOST
            - name: DB_PORT
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: DB_PORT
            # Through PgBouncer, not straight at Postgres. Locally compose has always
            # pointed here; the k8s manifests pointed at postgres:5432 directly, which
            # silently discarded the pooling that took 27 services down to 3 real
            # connections. prepareThreshold=0 is not optional with transaction pooling:
            # a server-side prepare made on one pooled connection is invisible on the
            # next, and the failure appears as an intermittent "prepared statement does
            # not exist" under load rather than at startup.
            - name: SPRING_DATASOURCE_URL
              value: jdbc:postgresql://$(DB_HOST):$(DB_PORT)/{db}?prepareThreshold=0
            - name: DB_REPLICA_HOST
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: DB_REPLICA_HOST
            - name: REDIS_HOST
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: REDIS_HOST
            - name: REDIS_PORT
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: REDIS_PORT
{extra_env}
            # The service runs as the unprivileged application role, so Postgres row-level
            # security applies to it. Flyway alone gets the owner credentials, to migrate and
            # to (re)apply the policies. The service refuses to start if this is a superuser.
            - name: SPRING_DATASOURCE_USERNAME
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: APP_DB_USERNAME
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: smartseason-secrets
                  key: APP_DB_PASSWORD
            - name: APP_DB_USER
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: APP_DB_USERNAME
            - name: SPRING_FLYWAY_USER
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: DB_USERNAME
            - name: SPRING_FLYWAY_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: smartseason-secrets
                  key: DB_PASSWORD
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: smartseason-secrets
                  key: JWT_SECRET
            - name: JWT_ISSUER
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: JWT_ISSUER
            - name: KAFKA_BOOTSTRAP_SERVERS
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: KAFKA_BOOTSTRAP_SERVERS
            # The outbox relay is off unless this says otherwise, and its code default is
            # false. A cluster that does not set it publishes nothing, silently.
            - name: EVENTS_ENABLED
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: EVENTS_ENABLED
            - name: LOG_LEVEL
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: LOG_LEVEL
          resources:
            requests:
              memory: "320Mi"
              cpu: "100m"
            limits:
              memory: "640Mi"
              cpu: "1000m"
          # Spring Boot needs a minute to bring up a context, a pool and run migrations,
          # so the liveness probe waits long enough not to kill a service that is simply
          # still starting.
          startupProbe:
            httpGet:
              path: /actuator/health/readiness
              port: {port}
            initialDelaySeconds: 20
            periodSeconds: 5
            failureThreshold: 30
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: {port}
            periodSeconds: 15
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: {port}
            periodSeconds: 10
            failureThreshold: 3
---
apiVersion: v1
kind: Service
metadata:
  name: {name}
  namespace: {ns}
spec:
  selector:
    app: {name}
  ports:
    - port: {port}
      targetPort: {port}
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {name}
  namespace: {ns}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {name}
  minReplicas: 2
  maxReplicas: 20
  metrics:
    # CPU at 70% of request, not of limit. Request is what the scheduler packs
    # against, so scaling on it keeps nodes from being oversubscribed.
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    # Memory is a weaker signal for a JVM - a heap that has grown does not shrink,
    # so utilisation stays high and never falls again. The threshold is set high
    # enough to catch real pressure without ratcheting replicas up permanently.
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 85
  behavior:
    scaleUp:
      # React quickly to load, but not to a single spike.
      stabilizationWindowSeconds: 60
      policies:
        - type: Percent
          value: 100
          periodSeconds: 60
    scaleDown:
      # Slowly. A Spring Boot service costs about a minute to start, so shedding
      # replicas eagerly means paying that again on the next rise.
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 1
          periodSeconds: 120
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: {name}
  namespace: {ns}
spec:
  # Voluntary disruption only - node drains, cluster upgrades. Without this a
  # drain can take every replica of a service at once and the service simply
  # disappears mid-upgrade.
  minAvailable: 1
  selector:
    matchLabels:
      app: {name}
"""


SHARED = """---
apiVersion: v1
kind: Namespace
metadata:
  name: {ns}
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: smartseason-config
  namespace: {ns}
data:
  # The pooler, not the database. See the datasource URL in each Deployment.
  DB_HOST: pgbouncer.{ns}.svc.cluster.local
  DB_PORT: "6432"
  # DB_USERNAME is the schema OWNER (Flyway, and the database server itself). The services run
  # as APP_DB_USERNAME, which owns nothing, so row-level security applies to it.
  DB_USERNAME: postgres
  APP_DB_USERNAME: smartseason_app
  # Absent until 2026-09-11, with no error anywhere: the rate limiter and the count
  # cache both fail open, so a cluster deploy would have run with neither and looked
  # healthy doing it.
  # Set this to a read replica's host to send every read-only transaction there. Blank
  # means there is no replica and all reads stay on the primary, which is the ceiling
  # this platform reaches first. Nothing else needs to change: the services already mark
  # their read paths @Transactional(readOnly = true).
  DB_REPLICA_HOST: ""
  # Object storage. media-service tracks MediaAsset/UploadTicket/MediaVariant rows but had
  # nowhere to put the bytes: there was no S3 client on the classpath at all, so "pre-signed
  # uploads" in its API description described an intention. MinIO is S3-compatible, so
  # pointing this at a managed bucket later is a config change.
  S3_ENDPOINT: http://objectstore.{ns}.svc.cluster.local:9000
  S3_BUCKET: smartseason-media
  S3_REGION: us-east-1
  REDIS_HOST: redis.{ns}.svc.cluster.local
  REDIS_PORT: "6379"
  JWT_ISSUER: smartseason-identity
  KAFKA_BOOTSTRAP_SERVERS: kafka:9092
  # The application code defaults this to false. Leaving it unset here means 27 services
  # write to their outbox tables and relay nothing — a silent failure with no error.
  EVENTS_ENABLED: "true"
  LOG_LEVEL: INFO
  # The public origin. Used for absolute links and as the gateway's allowed origin; its
  # code default is a localhost URL, which is correct locally and wrong everywhere else.
  PUBLIC_URL: https://{host}
---
apiVersion: v1
kind: Secret
metadata:
  name: smartseason-secrets
  namespace: {ns}
type: Opaque
stringData:
  # Replace before applying. The services refuse to start with a weak JWT secret outside
  # a dev profile, so this fails loudly rather than running insecurely.
  JWT_SECRET: "placeholder"
  DB_PASSWORD: "placeholder"
  APP_DB_PASSWORD: "placeholder"
  S3_ACCESS_KEY: "placeholder"
  S3_SECRET_KEY: "placeholder"
"""

EDGE = """---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
  namespace: {ns}
  labels:
    app: api-gateway
spec:
  replicas: 3
  selector:
    matchLabels:
      app: api-gateway
  template:
    metadata:
      labels:
        app: api-gateway
    spec:
      containers:
        - name: api-gateway
          image: smartseason/api-gateway:latest
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
          env:
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: smartseason-secrets
                  key: JWT_SECRET
            - name: JWT_ISSUER
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: JWT_ISSUER
            - name: PUBLIC_URL
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: PUBLIC_URL
            - name: LOG_LEVEL
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: LOG_LEVEL
          resources:
            requests: {{ memory: "256Mi", cpu: "100m" }}
            limits: {{ memory: "512Mi", cpu: "1000m" }}
          startupProbe:
            httpGet: {{ path: /actuator/health/readiness, port: 8080 }}
            initialDelaySeconds: 15
            periodSeconds: 5
            failureThreshold: 30
          readinessProbe:
            httpGet: {{ path: /actuator/health/readiness, port: 8080 }}
            periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: {ns}
spec:
  selector:
    app: api-gateway
  ports:
    - port: 8080
      targetPort: 8080
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: web
  namespace: {ns}
  labels:
    app: web
spec:
  replicas: 2
  selector:
    matchLabels:
      app: web
  template:
    metadata:
      labels:
        app: web
    spec:
      containers:
        - name: web
          image: smartseason/web:latest
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 3000
          resources:
            requests: {{ memory: "192Mi", cpu: "100m" }}
            limits: {{ memory: "512Mi", cpu: "1000m" }}
          readinessProbe:
            httpGet: {{ path: /api/health, port: 3000 }}
            initialDelaySeconds: 5
            periodSeconds: 10
          livenessProbe:
            httpGet: {{ path: /api/health, port: 3000 }}
            initialDelaySeconds: 20
            periodSeconds: 20
            failureThreshold: 3
---
apiVersion: v1
kind: Service
metadata:
  name: web
  namespace: {ns}
spec:
  selector:
    app: web
  ports:
    - port: 3000
      targetPort: 3000
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: api-gateway
  namespace: {ns}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: api-gateway
  # Every request passes through here, so it starts wider and grows further than
  # a domain service.
  minReplicas: 3
  maxReplicas: 40
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 65
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30
      policies:
        - type: Percent
          value: 100
          periodSeconds: 30
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Pods
          value: 2
          periodSeconds: 120
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: api-gateway
  namespace: {ns}
spec:
  # Two must survive a drain: losing the gateway takes the whole platform with it.
  minAvailable: 2
  selector:
    matchLabels:
      app: api-gateway
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: web
  namespace: {ns}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: web
  minReplicas: 2
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: web
  namespace: {ns}
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: web
"""

INGRESS = """---
# Single host, path routed: /api to the gateway, everything else to the front end. See
# the module docstring for why one origin rather than two.
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: smartseason
  namespace: {ns}
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-production
    # Media uploads go through the gateway; NGINX defaults to 1MB and would reject them
    # with a 413 before any service saw the request.
    nginx.ingress.kubernetes.io/proxy-body-size: "25m"
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/force-ssl-redirect: "true"
    nginx.ingress.kubernetes.io/use-forwarded-headers: "true"
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - {host}
      secretName: smartseason-tls
  rules:
    - host: {host}
      http:
        paths:
          # Longest prefix first, or every request reaches the front end and the API is
          # unreachable.
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: api-gateway
                port:
                  number: 8080
          - path: /
            pathType: Prefix
            backend:
              service:
                name: web
                port:
                  number: 3000
---
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-staging
spec:
  acme:
    server: https://acme-staging-v02.api.letsencrypt.org/directory
    email: admin@{host}
    privateKeySecretRef:
      name: letsencrypt-staging-account-key
    solvers:
      - http01:
          ingress:
            ingressClassName: nginx
---
# Use staging first. Production rate-limits at five duplicate certificates a week, and a
# wrong DNS record spends that allowance in an afternoon.
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-production
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@{host}
    privateKeySecretRef:
      name: letsencrypt-production-account-key
    solvers:
      - http01:
          ingress:
            ingressClassName: nginx
"""

NETPOL = """---
# Allow-list networking. The default deny below is what gives the rules after it meaning:
# without it, adding an allow rule changes nothing.
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-ingress
  namespace: {ns}
spec:
  podSelector: {{}}
  policyTypes:
    - Ingress
---
# The two pods meant to be reachable from outside. The ingress controller runs in its own
# namespace, so this cannot be narrowed to in-namespace traffic.
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-public-edge
  namespace: {ns}
spec:
  podSelector:
    matchExpressions:
      - key: app
        operator: In
        values: [api-gateway, web]
  policyTypes:
    - Ingress
  ingress:
    - {{}}
---
# Services talk to each other and to the gateway, but nothing outside the namespace
# reaches them directly.
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-namespace-internal
  namespace: {ns}
spec:
  podSelector:
    matchExpressions:
      - key: app
        operator: NotIn
        values: [api-gateway, web]
  policyTypes:
    - Ingress
  ingress:
    - from:
        - podSelector: {{}}
---
# cert-manager answers the ACME challenge with a short-lived solver pod in this namespace,
# which the default deny selects like any other. Without this the certificate never
# issues and the symptom points at cert-manager rather than at the network policy.
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: allow-acme-solver
  namespace: {ns}
spec:
  podSelector:
    matchLabels:
      acme.cert-manager.io/http01-solver: "true"
  policyTypes:
    - Ingress
  ingress:
    - {{}}
"""


# Extra environment for the few services that need something the other 26 do not. Kept
# per-service on purpose: putting S3 credentials into all 27 Deployments would hand every
# service a bucket key it has no use for, which is the sort of thing that is only noticed
# after something reads one.
EXTRA_ENV = {
    "media-service": """            - name: S3_ENDPOINT
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: S3_ENDPOINT
            - name: S3_BUCKET
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: S3_BUCKET
            - name: S3_REGION
              valueFrom:
                configMapKeyRef:
                  name: smartseason-config
                  key: S3_REGION
            - name: S3_ACCESS_KEY
              valueFrom:
                secretKeyRef:
                  name: smartseason-secrets
                  key: S3_ACCESS_KEY
            - name: S3_SECRET_KEY
              valueFrom:
                secretKeyRef:
                  name: smartseason-secrets
                  key: S3_SECRET_KEY
""",
}


def main():
    os.makedirs(OUT, exist_ok=True)
    written = []

    for filename, body in [
        ("00-namespace-config.yaml", SHARED),
        ("01-edge.yaml", EDGE),
        ("02-ingress.yaml", INGRESS),
        ("03-network-policy.yaml", NETPOL),
    ]:
        path = os.path.join(OUT, filename)
        with open(path, "w") as fh:
            fh.write(body.format(ns=NAMESPACE, host=HOST))
        written.append(path)

    ini = open(os.path.join(ROOT, "infra/docker/pgbouncer/pgbouncer.ini")).read()
    entry = open(os.path.join(ROOT, "infra/docker/pgbouncer/entrypoint.sh")).read()
    path = os.path.join(OUT, "00a-data-layer.yaml")
    with open(path, "w") as fh:
        fh.write(tpl_data.build(NAMESPACE, SERVICES, ini, entry))
    written.append(path)

    for spec in SERVICES:
        name = spec["name"]
        # From the catalogue, never derived. `device-registry-service` owns `device_db`
        # and `telemetry-ingest-service` owns `telemetry_db`; string surgery on the
        # service name invented two databases that do not exist.
        db = spec["db"]
        body = DEPLOYMENT.format(name=name, ns=NAMESPACE, port=spec["port"], db=db,
                                 extra_env=EXTRA_ENV.get(name, ""))
        path = os.path.join(OUT, f"{name}.yaml")
        with open(path, "w") as fh:
            fh.write(body)
        written.append(path)

    print(f"{len(written)} manifests written to k8s/")


if __name__ == "__main__":
    main()
