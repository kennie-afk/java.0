#!/usr/bin/env python3
"""Generates k8s/*.yaml. The four workloads differ only in a table, so one place writes them and
scripts/check-k8s.py fails if the committed files drift from this output.

    python3 scripts/gen-k8s.py            # write k8s/
    python3 scripts/gen-k8s.py --check    # exit 1 if k8s/ differs from what this would write
"""
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "k8s")
NS = "mara"
TAG = "1.0.0"
DB_USER_KEYS = [("MARA_DB_OWNER_PASSWORD", "MARA_DB_OWNER_PASSWORD"), ("MARA_DB_APP_PASSWORD", "MARA_DB_APP_PASSWORD")]


def lbl(name, extra=""):
    return f"{{app.kubernetes.io/name: {name}, app.kubernetes.io/part-of: mara{extra}}}"


# name, port, image, uid, db, replicas, (hpa min, max), cpu request/limit, memory request/limit, credential env (name -> secret key)
JVM = {
    "identity-service": dict(port=8081, db="mara_identity", replicas=2, hpa=(2, 8), cpu=("250m", "1"), mem=("512Mi", "768Mi"),
                             creds={"MARA_SVC_SYNC_CREDENTIAL": "MARA_SVC_SYNC_CREDENTIAL",
                                    "MARA_SVC_CORE_CREDENTIAL": "MARA_SVC_CORE_CREDENTIAL"},
                             optional_creds={"MARA_BOOTSTRAP_CREDENTIAL": "MARA_BOOTSTRAP_CREDENTIAL"},
                             extra_env=[]),
    "sync-service": dict(port=8082, db="mara_sync", replicas=3, hpa=(3, 20), cpu=("500m", "2"), mem=("512Mi", "1Gi"),
                         creds={"MARA_SERVICE_CREDENTIAL": "MARA_SVC_SYNC_CREDENTIAL"}, optional_creds={},
                         extra_env=[("MARA_IDENTITY_URL", "cfg", "MARA_IDENTITY_URL")]),
    "core-service": dict(port=8083, db="mara_core", replicas=3, hpa=(3, 20), cpu=("500m", "2"), mem=("512Mi", "1Gi"),
                         creds={"MARA_SERVICE_CREDENTIAL": "MARA_SVC_CORE_CREDENTIAL"}, optional_creds={},
                         extra_env=[("MARA_IDENTITY_URL", "cfg", "MARA_IDENTITY_URL"), ("MARA_SYNC_URL", "cfg", "MARA_SYNC_URL"),
                                    ("MARA_FISCAL_LEASE_SIZE", "cfg", "MARA_FISCAL_LEASE_SIZE")]),
}
IMAGE = {"identity-service": "mara-identity-service", "sync-service": "mara-sync-service", "core-service": "mara-core-service",
         "terminal": "mara-terminal", "office": "mara-office"}


def cfg_env(name, key):
    return f"            - name: {name}\n              valueFrom: {{configMapKeyRef: {{name: mara-config, key: {key}}}}}\n"


def secret_env(name, key, optional=False):
    opt = ", optional: true" if optional else ""
    return f"            - name: {name}\n              valueFrom: {{secretKeyRef: {{name: mara-secrets, key: {key}{opt}}}}}\n"


def common_tail(name, replicas_min, replicas_max, cpu_target=70):
    return f"""---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {name}
  namespace: {NS}
spec:
  scaleTargetRef: {{apiVersion: apps/v1, kind: Deployment, name: {name}}}
  minReplicas: {replicas_min}
  maxReplicas: {replicas_max}
  metrics:
    - type: Resource
      resource: {{name: cpu, target: {{type: Utilization, averageUtilization: {cpu_target}}}}}
  behavior:
    # A JVM service takes about a minute to start, so scale up promptly and down slowly.
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
        - {{type: Percent, value: 100, periodSeconds: 60}}
    scaleDown:
      stabilizationWindowSeconds: 300
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: {name}
  namespace: {NS}
spec:
  minAvailable: 1
  selector:
    matchLabels: {{app.kubernetes.io/name: {name}}}
"""


def jvm_workload(name, d):
    env = cfg_env("MARA_DB_OWNER_USER", "MARA_DB_OWNER_USER") + cfg_env("MARA_DB_APP_USER", "MARA_DB_APP_USER")
    env += secret_env("MARA_DB_OWNER_PASSWORD", "MARA_DB_OWNER_PASSWORD") + secret_env("MARA_DB_APP_PASSWORD", "MARA_DB_APP_PASSWORD")
    env += f"            - {{name: MARA_DB_URL, value: \"jdbc:postgresql://postgres:5432/{d['db']}\"}}\n"
    for k, key in [("MARA_REDIS_URL", "MARA_REDIS_URL"), ("MARA_RATELIMIT_TRUST_FORWARDED_FOR", "MARA_RATELIMIT_TRUST_FORWARDED_FOR"),
                   ("MARA_DB_POOL_SIZE", "MARA_DB_POOL_SIZE")]:
        env += cfg_env(k, key)
    for k, kind, key in d["extra_env"]:
        env += cfg_env(k, key)
    for k, key in d["creds"].items():
        env += secret_env(k, key)
    for k, key in d["optional_creds"].items():
        env += secret_env(k, key, optional=True)
    if name == "identity-service":
        env += cfg_env("MARA_RATELIMIT_ENROLMENT_PER_MINUTE", "MARA_RATELIMIT_ENROLMENT_PER_MINUTE")
        env += cfg_env("MARA_RATELIMIT_SIGNIN_PER_MINUTE", "MARA_RATELIMIT_SIGNIN_PER_MINUTE")
    else:
        env += cfg_env("MARA_RATELIMIT_TERMINAL_PER_MINUTE", "MARA_RATELIMIT_TERMINAL_PER_MINUTE")
    env += "            - {name: JAVA_TOOL_OPTIONS, value: \"-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError\"}\n"
    img = f"{IMAGE[name]}:{TAG}"
    p = d["port"]
    return f"""apiVersion: v1
kind: Service
metadata:
  name: {name}
  namespace: {NS}
  labels: {lbl(name)}
spec:
  selector: {{app.kubernetes.io/name: {name}}}
  ports:
    - {{name: http, port: {p}, targetPort: {p}}}
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {name}
  namespace: {NS}
  labels: {lbl(name)}
spec:
  replicas: {d['replicas']}
  revisionHistoryLimit: 5
  strategy:
    type: RollingUpdate
    rollingUpdate: {{maxUnavailable: 0, maxSurge: 1}}
  selector:
    matchLabels: {{app.kubernetes.io/name: {name}}}
  template:
    metadata:
      labels: {lbl(name)}
    spec:
      automountServiceAccountToken: false
      terminationGracePeriodSeconds: 30
      securityContext:
        runAsNonRoot: true
        runAsUser: 10001
        runAsGroup: 10001
        seccompProfile: {{type: RuntimeDefault}}
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: kubernetes.io/hostname
          whenUnsatisfiable: ScheduleAnyway
          labelSelector:
            matchLabels: {{app.kubernetes.io/name: {name}}}
      initContainers:
        # The service exits if its database is unreachable at start-up, which would crash-loop it while Postgres comes
        # up. Waiting here keeps restarts meaningful. It reuses the service image, so no extra image is needed.
        - name: wait-for-postgres
          image: {img}
          imagePullPolicy: IfNotPresent
          command: [sh, -c, "until nc -z postgres 5432; do echo waiting for postgres; sleep 2; done"]
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities: {{drop: [ALL]}}
          resources:
            requests: {{cpu: 10m, memory: 16Mi}}
            limits: {{cpu: 100m, memory: 64Mi}}
      containers:
        - name: {name}
          image: {img}
          imagePullPolicy: IfNotPresent
          ports:
            - {{name: http, containerPort: {p}}}
          env:
{env}          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities: {{drop: [ALL]}}
          # Migrations run at start-up, so a first start can be slow: the startup probe allows 3 minutes.
          startupProbe:
            httpGet: {{path: /actuator/health/liveness, port: http}}
            periodSeconds: 5
            failureThreshold: 36
          readinessProbe:
            httpGet: {{path: /actuator/health/readiness, port: http}}
            periodSeconds: 10
            timeoutSeconds: 3
          livenessProbe:
            httpGet: {{path: /actuator/health/liveness, port: http}}
            periodSeconds: 20
            timeoutSeconds: 3
            failureThreshold: 3
          resources:
            requests: {{cpu: {d['cpu'][0]}, memory: {d['mem'][0]}}}
            limits: {{cpu: "{d['cpu'][1]}", memory: {d['mem'][1]}}}
          volumeMounts:
            - {{name: tmp, mountPath: /tmp}}
      volumes:
        - name: tmp
          emptyDir: {{}}
""" + common_tail(name, d["hpa"][0], d["hpa"][1])


def web_workload(name, port, uid, env, cpu_req="100m", mem_req="192Mi", cpu_lim="1", mem_lim="384Mi", hpa=(2, 10)):
    return f"""apiVersion: v1
kind: Service
metadata:
  name: {name}
  namespace: {NS}
  labels: {lbl(name)}
spec:
  selector: {{app.kubernetes.io/name: {name}}}
  ports:
    - {{name: http, port: {port}, targetPort: {port}}}
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {name}
  namespace: {NS}
  labels: {lbl(name)}
spec:
  replicas: 2
  revisionHistoryLimit: 5
  strategy:
    type: RollingUpdate
    rollingUpdate: {{maxUnavailable: 0, maxSurge: 1}}
  selector:
    matchLabels: {{app.kubernetes.io/name: {name}}}
  template:
    metadata:
      labels: {lbl(name)}
    spec:
      automountServiceAccountToken: false
      terminationGracePeriodSeconds: 30
      securityContext:
        runAsNonRoot: true
        runAsUser: {uid}
        runAsGroup: {uid}
        seccompProfile: {{type: RuntimeDefault}}
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: kubernetes.io/hostname
          whenUnsatisfiable: ScheduleAnyway
          labelSelector:
            matchLabels: {{app.kubernetes.io/name: {name}}}
      containers:
        - name: {name}
          image: {IMAGE[name]}:{TAG}
          imagePullPolicy: IfNotPresent
          ports:
            - {{name: http, containerPort: {port}}}
          env:
{env}          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities: {{drop: [ALL]}}
          startupProbe:
            httpGet: {{path: /, port: http}}
            periodSeconds: 3
            failureThreshold: 30
          readinessProbe:
            httpGet: {{path: /, port: http}}
            periodSeconds: 10
            timeoutSeconds: 3
          livenessProbe:
            httpGet: {{path: /, port: http}}
            periodSeconds: 20
            timeoutSeconds: 3
            failureThreshold: 3
          resources:
            requests: {{cpu: {cpu_req}, memory: {mem_req}}}
            limits: {{cpu: "{cpu_lim}", memory: {mem_lim}}}
          volumeMounts:
            - {{name: tmp, mountPath: /tmp}}
      volumes:
        - name: tmp
          emptyDir: {{}}
""" + common_tail(name, hpa[0], hpa[1])


def terminal_workload():
    env = (cfg_env("IDENTITY_BASE_URL", "MARA_IDENTITY_URL") + cfg_env("SYNC_BASE_URL", "MARA_SYNC_URL")
           + cfg_env("CORE_BASE_URL", "MARA_CORE_URL") + cfg_env("MPESA_MODE", "MARA_MPESA_MODE"))
    return web_workload("terminal", 3100, 1000, env)


def office_workload():
    env = (cfg_env("IDENTITY_BASE_URL", "MARA_IDENTITY_URL") + cfg_env("SYNC_BASE_URL", "MARA_SYNC_URL")
           + cfg_env("CORE_BASE_URL", "MARA_CORE_URL") + secret_env("OFFICE_SESSION_SECRET", "OFFICE_SESSION_SECRET")
           + "            - {name: OFFICE_COOKIE_SECURE, value: \"true\"}\n")
    # the owners' console: light traffic, never needs many replicas
    return web_workload("office", 3200, 1000, env, hpa=(2, 4))


FILES = {}

FILES["00-namespace.yaml"] = f"""apiVersion: v1
kind: Namespace
metadata:
  name: {NS}
  labels:
    pod-security.kubernetes.io/enforce: restricted
    pod-security.kubernetes.io/audit: restricted
    pod-security.kubernetes.io/warn: restricted
"""

FILES["01-config.yaml"] = f"""apiVersion: v1
kind: ConfigMap
metadata:
  name: mara-config
  namespace: {NS}
data:
  MARA_DB_OWNER_USER: mara_owner
  MARA_DB_APP_USER: mara_app
  MARA_IDENTITY_URL: http://identity-service:8081
  MARA_SYNC_URL: http://sync-service:8082
  MARA_CORE_URL: http://core-service:8083
  # Shared rate-limit counters. If Redis is unreachable each service falls back to its own in-process limit (it neither
  # opens up nor refuses the tills), so this is not a hard dependency.
  MARA_REDIS_URL: redis://redis:6379
  # The ingress appends the real client address to X-Forwarded-For and the terminal proxy passes it on; counting that
  # instead of the proxy pod's address is what gives every shop its own allowance. Safe only because the NetworkPolicy
  # lets nothing but the ingress controller reach the terminal pods. Do not set this where that is not true.
  MARA_RATELIMIT_TRUST_FORWARDED_FOR: "true"
  MARA_RATELIMIT_TERMINAL_PER_MINUTE: "1200"
  MARA_RATELIMIT_ENROLMENT_PER_MINUTE: "20"
  MARA_RATELIMIT_SIGNIN_PER_MINUTE: "120"
  # Database connections per replica. Postgres connections = replicas x this x 3 services: size it against max_connections
  # (or put PgBouncer in front; see docs/OPERATIONS.md, which says what has and has not been tested).
  MARA_DB_POOL_SIZE: "10"
  MARA_FISCAL_LEASE_SIZE: "5000"
  # Only "mock" exists: the M-Pesa prompt on the till is simulated and moves no money.
  MARA_MPESA_MODE: mock
"""

FILES["02-secret.example.yaml"] = f"""# NOT applied by kustomize. Shows the keys the workloads read. Create the real Secret with scripts/k8s-secret.sh, which
# generates random values and never writes them to a file:  ./scripts/k8s-secret.sh | kubectl apply -f -
# Each service receives ONLY its own credential (see scripts/check-k8s.py): sync-service reads MARA_SVC_SYNC_CREDENTIAL,
# core-service MARA_SVC_CORE_CREDENTIAL; identity-service reads both (it registers their hashes) and the optional bootstrap one.
apiVersion: v1
kind: Secret
metadata:
  name: mara-secrets
  namespace: {NS}
type: Opaque
stringData:
  MARA_DB_OWNER_PASSWORD: CHANGE-ME
  MARA_DB_APP_PASSWORD: CHANGE-ME
  MARA_SVC_SYNC_CREDENTIAL: mop_CHANGE-ME
  MARA_SVC_CORE_CREDENTIAL: mop_CHANGE-ME
  # Seals the back office's sign-in cookie (32+ random characters); read only by the office pods.
  OFFICE_SESSION_SECRET: CHANGE-ME-32-OR-MORE-RANDOM-CHARACTERS
  # Optional (24 h, can only manage credentials): mint the first platform operator, then remove it and restart identity.
  MARA_BOOTSTRAP_CREDENTIAL: mop_CHANGE-ME
"""

FILES["10-postgres.yaml"] = f"""# One Postgres for small deployments and for proving these manifests. It is a single instance: no standby, no failover.
# For real tenants use a managed or replicated Postgres (an operator such as CloudNativePG, or the provider's service) and
# change the host in the services' MARA_DB_URL; the services need only the owner (migrations) and app (runtime) roles.
# The services create their own databases (mara_sync, mara_core) on first start; mara_identity is created here.
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: {NS}
  labels: {lbl('postgres')}
spec:
  clusterIP: None
  selector: {{app.kubernetes.io/name: postgres}}
  ports:
    - {{name: postgres, port: 5432, targetPort: 5432}}
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: {NS}
  labels: {lbl('postgres')}
spec:
  serviceName: postgres
  replicas: 1
  selector:
    matchLabels: {{app.kubernetes.io/name: postgres}}
  template:
    metadata:
      labels: {lbl('postgres')}
    spec:
      automountServiceAccountToken: false
      terminationGracePeriodSeconds: 60
      securityContext:
        runAsNonRoot: true
        runAsUser: 70
        runAsGroup: 70
        fsGroup: 70
        seccompProfile: {{type: RuntimeDefault}}
      containers:
        - name: postgres
          image: postgres:16-alpine
          ports:
            - {{name: postgres, containerPort: 5432}}
          args: [postgres, -c, max_connections=300, -c, shared_buffers=512MB, -c, wal_compression=on]
          env:
            - {{name: POSTGRES_DB, value: mara_identity}}
            - name: POSTGRES_USER
              valueFrom: {{configMapKeyRef: {{name: mara-config, key: MARA_DB_OWNER_USER}}}}
            - name: POSTGRES_PASSWORD
              valueFrom: {{secretKeyRef: {{name: mara-secrets, key: MARA_DB_OWNER_PASSWORD}}}}
            - {{name: PGDATA, value: /var/lib/postgresql/data/pgdata}}
          securityContext:
            allowPrivilegeEscalation: false
            capabilities: {{drop: [ALL]}}
          readinessProbe:
            exec: {{command: [sh, -c, "pg_isready -U \\"$POSTGRES_USER\\" -d \\"$POSTGRES_DB\\""]}}
            periodSeconds: 10
            timeoutSeconds: 5
          livenessProbe:
            exec: {{command: [sh, -c, "pg_isready -U \\"$POSTGRES_USER\\" -d \\"$POSTGRES_DB\\""]}}
            initialDelaySeconds: 30
            periodSeconds: 20
            timeoutSeconds: 5
          startupProbe:
            exec: {{command: [sh, -c, "pg_isready -U \\"$POSTGRES_USER\\" -d \\"$POSTGRES_DB\\""]}}
            periodSeconds: 5
            failureThreshold: 36
          resources:
            requests: {{cpu: 500m, memory: 1Gi}}
            limits: {{cpu: "4", memory: 3Gi}}
          volumeMounts:
            - {{name: data, mountPath: /var/lib/postgresql/data}}
            - {{name: run, mountPath: /var/run/postgresql}}
            - {{name: shm, mountPath: /dev/shm}}
      volumes:
        - name: run
          emptyDir: {{}}
        # Postgres wants more shared memory than a container's default 64 MB.
        - name: shm
          emptyDir: {{medium: Memory, sizeLimit: 512Mi}}
  volumeClaimTemplates:
    - metadata:
        name: data
      spec:
        accessModes: [ReadWriteOnce]
        resources:
          requests: {{storage: 50Gi}}
"""

FILES["11-redis.yaml"] = f"""# Shared rate-limit counters only. Nothing durable lives here (no volume, no persistence on purpose): losing it resets
# every counter, and while it is down each service limits on its own.
apiVersion: v1
kind: Service
metadata:
  name: redis
  namespace: {NS}
  labels: {lbl('redis')}
spec:
  selector: {{app.kubernetes.io/name: redis}}
  ports:
    - {{name: redis, port: 6379, targetPort: 6379}}
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
  namespace: {NS}
  labels: {lbl('redis')}
spec:
  replicas: 1
  selector:
    matchLabels: {{app.kubernetes.io/name: redis}}
  template:
    metadata:
      labels: {lbl('redis')}
    spec:
      automountServiceAccountToken: false
      securityContext:
        runAsNonRoot: true
        runAsUser: 999
        runAsGroup: 999
        seccompProfile: {{type: RuntimeDefault}}
      containers:
        - name: redis
          image: redis:7-alpine
          args: [redis-server, --save, "", --appendonly, "no", --maxmemory, 64mb, --maxmemory-policy, volatile-ttl]
          ports:
            - {{name: redis, containerPort: 6379}}
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities: {{drop: [ALL]}}
          startupProbe:
            exec: {{command: [redis-cli, ping]}}
            periodSeconds: 2
            failureThreshold: 30
          readinessProbe:
            exec: {{command: [redis-cli, ping]}}
            periodSeconds: 10
          livenessProbe:
            exec: {{command: [redis-cli, ping]}}
            periodSeconds: 20
          resources:
            requests: {{cpu: 50m, memory: 64Mi}}
            limits: {{cpu: 500m, memory: 128Mi}}
          volumeMounts:
            - {{name: tmp, mountPath: /tmp}}
      volumes:
        - name: tmp
          emptyDir: {{}}
"""

FILES["20-identity.yaml"] = jvm_workload("identity-service", JVM["identity-service"])
FILES["21-sync.yaml"] = jvm_workload("sync-service", JVM["sync-service"])
FILES["22-core.yaml"] = jvm_workload("core-service", JVM["core-service"])
FILES["30-terminal.yaml"] = terminal_workload()
FILES["31-office.yaml"] = office_workload()

FILES["40-ingress.yaml"] = f"""# Only the till and the owners' back office are public. The three services are reachable inside the namespace only (and by `kubectl port-forward` for an
# operator's back-office calls); the actuator is never routed. TLS terminates here: set the real host and the TLS Secret.
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: terminal
  namespace: {NS}
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    # Journal uploads carry up to 4 MiB of signed entries.
    nginx.ingress.kubernetes.io/proxy-body-size: 5m
    nginx.ingress.kubernetes.io/proxy-read-timeout: "30"
spec:
  ingressClassName: nginx
  tls:
    - hosts: [mara.example.org, office.mara.example.org]
      secretName: mara-tls
  rules:
    - host: mara.example.org
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service: {{name: terminal, port: {{number: 3100}}}}
    - host: office.mara.example.org
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service: {{name: office, port: {{number: 3200}}}}
"""


def np(name, selector, ingress=None, egress=None):
    sel = "{}" if selector is None else "{matchLabels: {app.kubernetes.io/name: %s}}" % selector
    types = [t for t, v in (("Ingress", ingress), ("Egress", egress)) if v is not None]
    out = f"""---
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: {name}
  namespace: {NS}
spec:
  podSelector: {sel}
  policyTypes: [{', '.join(types)}]
"""
    if ingress:
        out += "  ingress:\n" + "".join(ingress)
    if egress:
        out += "  egress:\n" + "".join(egress)
    return out


def peer(name, port):
    return f"    - from:\n        - podSelector: {{matchLabels: {{app.kubernetes.io/name: {name}}}}}\n      ports:\n        - {{protocol: TCP, port: {port}}}\n"


def to(name, port):
    return f"    - to:\n        - podSelector: {{matchLabels: {{app.kubernetes.io/name: {name}}}}}\n      ports:\n        - {{protocol: TCP, port: {port}}}\n"


dns = """    - to:
        - namespaceSelector: {matchLabels: {kubernetes.io/metadata.name: kube-system}}
      ports:
        - {protocol: UDP, port: 53}
        - {protocol: TCP, port: 53}
"""
def ingress_ctl(port):
    return f"""    - from:
        - namespaceSelector: {{matchLabels: {{kubernetes.io/metadata.name: ingress-nginx}}}}
      ports:
        - {{protocol: TCP, port: {port}}}
"""
FILES["50-network-policy.yaml"] = (
    "# Default deny, then exactly the calls the system makes. Written so that nothing but the ingress controller can reach\n"
    "# the terminal and office pods: the rate limits trust the address the ingress appended (MARA_RATELIMIT_TRUST_FORWARDED_FOR), which is\n"
    "# only sound under that condition. If you change this file, keep it true.\n"
    + np("default-deny", None, ingress=[], egress=[]).replace("  ingress:\n", "").replace("  egress:\n", "").replace("---\n", "", 1)
    + np("allow-dns", None, egress=[dns])
    + np("terminal", "terminal", ingress=[ingress_ctl(3100)], egress=[to("identity-service", 8081), to("sync-service", 8082), to("core-service", 8083)])
    + np("office", "office", ingress=[ingress_ctl(3200)], egress=[to("identity-service", 8081), to("sync-service", 8082), to("core-service", 8083)])
    + np("identity-service", "identity-service",
         ingress=[peer("terminal", 8081), peer("office", 8081), peer("sync-service", 8081), peer("core-service", 8081)],
         egress=[to("postgres", 5432), to("redis", 6379)])
    + np("sync-service", "sync-service", ingress=[peer("terminal", 8082), peer("office", 8082), peer("core-service", 8082)],
         egress=[to("postgres", 5432), to("redis", 6379), to("identity-service", 8081)])
    + np("core-service", "core-service", ingress=[peer("terminal", 8083), peer("office", 8083)],
         egress=[to("postgres", 5432), to("redis", 6379), to("identity-service", 8081), to("sync-service", 8082)])
    + np("postgres", "postgres", ingress=[peer("identity-service", 5432), peer("sync-service", 5432), peer("core-service", 5432)])
    + np("redis", "redis", ingress=[peer("identity-service", 6379), peer("sync-service", 6379), peer("core-service", 6379)])
)

FILES["kustomization.yaml"] = f"""apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: {NS}
resources:
{chr(10).join('  - ' + f for f in sorted(FILES) if f not in ('02-secret.example.yaml', 'kustomization.yaml'))}
"""


def main():
    check = "--check" in sys.argv
    os.makedirs(OUT, exist_ok=True)
    bad = []
    for name, text in FILES.items():
        path = os.path.join(OUT, name)
        if check:
            if not os.path.exists(path) or open(path).read() != text:
                bad.append(name)
        else:
            open(path, "w").write(text)
    if check and bad:
        print("k8s/ differs from scripts/gen-k8s.py output: " + ", ".join(bad))
        sys.exit(1)
    print(("k8s/ matches the generator" if check else f"wrote {len(FILES)} files to k8s/"))


if __name__ == "__main__":
    main()
