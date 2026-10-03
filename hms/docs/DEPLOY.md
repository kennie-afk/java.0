# Deploying HMS

## One machine: Docker Compose

```bash
cp .env.example .env     # replace the three secrets
docker compose up --build
scripts/demo_seed/run.sh # optional: Lakeview Community Hospital sample data (needs an empty database)
```

## Kubernetes

`k8s/` holds the manifests (apply with `kubectl apply -k k8s/` after the steps below):

| File | What |
| --- | --- |
| `00-namespace.yaml` | namespace `hms`, pod security "restricted" enforced |
| `01-config.yaml` | non-secret settings; **set `HMS_ALLOWED_ORIGINS` to the real host** |
| `02-secret.example.yaml` | the keys of the Secret (not applied) |
| `10-postgres.yaml` | one Postgres with a 20Gi volume, for small deployments |
| `20-api.yaml`, `30-console.yaml` | Deployments (2 replicas), Services, autoscalers (CPU 70%), disruption budgets, probes |
| `40-ingress.yaml` | console at `/`; the FHIR interface and login at `/fhir` and `/v1/auth/login`, with a rate limit |
| `50-network-policy.yaml` | default deny; console to API, API to Postgres, ingress to console and API |

Steps:

1. Build and push the two images and put their real names and tags in `20-api.yaml` and `30-console.yaml` (the manifests use `hms-api:1.0.0` and `hms-console:1.0.0`; `latest` is refused by the checks).
2. Replace `hms.example.org` in `40-ingress.yaml` and `HMS_ALLOWED_ORIGINS` in `01-config.yaml`.
3. `kubectl apply -f k8s/00-namespace.yaml`, then `./scripts/k8s-secret.sh | kubectl apply -f -` **once** (it generates random secrets and writes no file), then `kubectl apply -k k8s/`.

For a real hospital, replace `10-postgres.yaml` with a managed or replicated Postgres and point `HMS_DB_URL` at it. The API needs an owner role (migrations) and creates the least-privilege application role itself.

### Files and messages

- **Imaging images** are stored by the API on local disk (`HMS_STORAGE_PATH`; Compose mounts a named volume at `/data/objects`; back it up with the database). That is fine for one node. The Kubernetes config switches uploads **off** (`HMS_STORAGE_UPLOADS: "false"`) because pod disks are neither durable nor shared between replicas, and an S3-compatible store is not built. Turn them on only for a single API replica with a PersistentVolume at `HMS_STORAGE_PATH`.
- **E-mail and SMS** (`HMS_NOTIFICATIONS_MODE`): `mock` logs a masked line and sends nothing; `live` is not implemented and fails each message loudly. Set `HMS_NOTIFICATIONS_DISPATCHER=false` on any replica that should not deliver.

### Checks

```bash
python3 scripts/check-k8s.py            # static rules: non-root, probes, resources, pinned images, policies, references
docker run --rm -v "$PWD/k8s":/k8s ghcr.io/yannh/kubeconform:v0.6.7 -strict -summary \
  -ignore-filename-pattern 'kustomization.yaml|secret.example' /k8s
```

### What was and was not verified (2026-10-03)

Applied to a throwaway kind cluster (Kubernetes 1.31), one replica each:

- The namespace's restricted pod security accepted every pod; the API and console run as non-root (10001 and 1000) with a read-only root filesystem and a writable `/tmp`.
- All 16 migrations applied in the cluster; sign-in, organisation creation, the FHIR `metadata` endpoint and the console proxy worked.
- The network policies were enforced in that cluster: the console could not reach Postgres.
- The API crash-looped five times while Postgres was starting, so an init container now waits for it; after that it started with no restarts.

Not verified: the autoscalers (the throwaway cluster had no metrics server, so they were deleted for the test), two replicas of each workload (memory), the ingress, TLS and cert-manager, the Postgres volume surviving a node restart, and any load test.
