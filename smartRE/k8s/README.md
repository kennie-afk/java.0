# SmartRE Kenya — Kubernetes Deployment

## Prerequisites
- kubectl configured for your cluster
- Docker images built and pushed to your registry
- Helm installed (for Redis and Kafka)
- An **NGINX ingress controller** and **cert-manager** (see Ingress and TLS below)
- DNS for `smartre.co.ke` and `www.smartre.co.ke` pointing at the ingress controller's
  external IP, **before** you apply the Ingress — the ACME challenge fails otherwise

## Deploy Infrastructure

```bash
# Redis (HA with Sentinel)
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install redis bitnami/redis \
  --namespace smartre \
  --set auth.enabled=false \
  --set sentinel.enabled=true \
  --set sentinel.quorum=2 \
  --set replica.replicaCount=3

# Kafka (KRaft mode, no Zookeeper)
helm install kafka bitnami/kafka \
  --namespace smartre \
  --set replicaCount=3 \
  --set kraft.enabled=true \
  --set provisioning.topics[0].name=verification-events \
  --set provisioning.topics[0].partitions=6 \
  --set provisioning.topics[0].replicationFactor=3
```

## Build and Push Images

```bash
# All nine JVM services. The list must stay in step with k8s/*.yaml — scripts/check-k8s-env.py
# will tell you if a service has a manifest, but not if you forgot to build its image.
for svc in user-service verification-service property-service viewing-service \
           payment-service review-service notification-service \
           property-management-service api-gateway; do
  docker build -t smartre/$svc:latest ./$svc
  docker push your-registry/smartre/$svc:latest
done
```

The front end is built separately, because Next.js inlines every `NEXT_PUBLIC_*` value at
build time. These are baked into the image, not read from the ConfigMap at runtime, so an
image built with the wrong API URL will not be fixed by editing the ConfigMap — rebuild it.

`NEXT_PUBLIC_API_URL` is the **site's own origin**, not a separate API hostname. The
Ingress routes `/api` on that host to the gateway, so the browser only ever makes
same-origin requests. It must match `FRONTEND_URL` and `PUBLIC_API_URL` in the ConfigMap;
if they disagree, the app talks to one origin while the gateway and the emails assume
another.

```bash
docker build -t smartre/web:latest \
  --build-arg NEXT_PUBLIC_API_URL=https://smartre.co.ke \
  --build-arg NEXT_PUBLIC_S3_PUBLIC_URL=https://smartre-documents.s3.amazonaws.com \
  --build-arg NEXT_PUBLIC_GOOGLE_MAPS_API_KEY=... \
  ../smartRE-front
docker push your-registry/smartre/web:latest
```

## Ingress and TLS

Traffic reaches the cluster through one host, path-routed: `/api` to the gateway,
everything else to the front end. That is a deliberate choice over two hostnames — it
makes every API call same-origin, which removes CORS preflights, lets the session cookie
stay `SameSite=Lax`, and leaves one certificate to renew. The reasoning is written out at
the top of `ingress.yaml`.

```bash
# 1. Controller and cert-manager, once per cluster
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace

helm repo add jetstack https://charts.jetstack.io
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace --set crds.enabled=true

# 2. Point DNS at the controller before going further
kubectl get svc -n ingress-nginx ingress-nginx-controller \
  -o jsonpath='{.status.loadBalancer.ingress[0].ip}'

# 3. Issuers
kubectl apply -f k8s/cert-issuer.yaml
```

**Use the staging issuer first.** Let's Encrypt rate-limits production at five duplicate
certificates per week, and a wrong DNS record will spend that allowance in an afternoon,
locking you out until the window rolls. Edit `ingress.yaml` to
`cert-manager.io/cluster-issuer: letsencrypt-staging`, apply, and confirm:

```bash
kubectl apply -f k8s/ingress.yaml
kubectl describe certificate smartre-tls -n smartre     # want: Ready=True
kubectl get challenges -n smartre                        # want: empty once issued
```

A challenge that never clears is usually one of three things: DNS not yet propagated, the
controller not reachable on port 80 from the internet, or the `allow-acme-solver` rule in
`network-policy.yaml` missing — the namespace denies ingress by default, and cert-manager's
solver pod is selected by that deny like anything else.

Once a staging certificate issues, switch the annotation to `letsencrypt-production`,
delete the staging secret so a fresh one is requested, and re-apply:

```bash
kubectl delete secret smartre-tls -n smartre
kubectl apply -f k8s/ingress.yaml
```

**`/actuator` is deliberately not routed.** Probes and Prometheus reach it inside the
cluster; publishing it would hand an unauthenticated caller the service's configuration
and dependency graph.

## Before You Apply: Check the Environment Wiring

```bash
python3 scripts/check-k8s-env.py
```

This fails the build if a service reads a `${VAR}` with no default that no manifest sets
(the pod would crash-loop), if a manifest references a Secret or ConfigMap key that does
not exist (the pod would never start), or if a ConfigMap key is set but wired to nothing
(the setting would read as authoritative while being inert).

## Storage: Decide Before the First Upload

`S3_ENABLED` defaults to `false`, which makes user-service write uploaded documents to a
directory inside its own container. On Kubernetes that is ephemeral per-pod disk, and
user-service runs two replicas: a document uploaded through one pod is not visible to the
other, and is gone when the pod restarts. Set `S3_ENABLED: "true"` in the ConfigMap and
put real credentials in `S3_ACCESS_KEY` / `S3_SECRET_KEY` before anyone uploads anything
you intend to keep.

## Deploy Services

```bash
# Create namespace and secrets
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml

# Create secrets (edit secret.yaml with real values first)
kubectl apply -f k8s/secret.yaml

# Deploy all services
kubectl apply -f k8s/
```

## Scale Manually

```bash
# Scale property-service to 5 replicas for high traffic
kubectl scale deployment property-service -n smartre --replicas=5

# HPA will auto-scale based on CPU/memory when load increases
kubectl get hpa -n smartre
```

## Monitor

```bash
kubectl get pods -n smartre
kubectl logs -f deployment/property-service -n smartre
kubectl top pods -n smartre
```

## Resource Sizing for 1M+ Users (Production Recommendations)

| Service | Min Replicas | Max (HPA) | Memory | CPU |
|---|---|---|---|---|
| api-gateway | 3 | 20 | 256Mi | 0.5 |
| user-service | 2 | 10 | 512Mi | 1.0 |
| verification-service | 2 | 8 | 512Mi | 1.0 |
| property-service | 3 | 15 | 512Mi | 1.0 |
| viewing-service | 2 | 8 | 256Mi | 0.5 |
| payment-service | 2 | 10 | 512Mi | 1.0 |
| review-service | 2 | 8 | 256Mi | 0.5 |
| notification-service | 2 | 6 | 256Mi | 0.5 |
| property-management-service | 2 | 8 | 512Mi | 1.0 |
| web | 2 | 8 | 192Mi | 0.5 |
