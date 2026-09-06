import json, random, statistics, sys, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor

API = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8090"
REQUESTS = int(sys.argv[2]) if len(sys.argv) > 2 else 500
WORKERS = int(sys.argv[3]) if len(sys.argv) > 3 else 16

def login(email):
    req = urllib.request.Request(f"{API}/v1/auth/login",
        data=json.dumps({"email": email, "password": "a-strong-demo-passphrase"}).encode(),
        headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())["accessToken"]

def get(path, token):
    req = urllib.request.Request(f"{API}{path}", headers={"Authorization": f"Bearer {token}"})
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(req) as r:
            r.read()
        return (time.perf_counter() - started) * 1000, True
    except Exception:
        return (time.perf_counter() - started) * 1000, False

def place(token, customer_id, product_ids):
    lines = [{"productId": p, "quantity": random.randint(1, 8)}
             for p in random.sample(product_ids, random.randint(1, 3))]
    body = json.dumps({"customerId": customer_id, "lines": lines}).encode()
    req = urllib.request.Request(f"{API}/v1/orders", data=body, method="POST",
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {token}"})
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(req) as r:
            r.read()
        return (time.perf_counter() - started) * 1000, True
    except urllib.error.HTTPError as e:
        return (time.perf_counter() - started) * 1000, e.code == 409
    except Exception:
        return (time.perf_counter() - started) * 1000, False

def summarise(label, samples, elapsed):
    times = [t for t, ok in samples]
    ok = sum(1 for _, o in samples if o)
    times.sort()
    p = lambda q: times[min(int(len(times) * q), len(times) - 1)]
    print(f"  {label:<22} n={len(samples):<5} ok={ok:<5} "
          f"p50={p(0.50):6.1f}ms  p95={p(0.95):6.1f}ms  p99={p(0.99):6.1f}ms  "
          f"{len(samples)/elapsed:7.1f} req/s")

if __name__ == "__main__":
    token = login("grace@mazingira.co.ke")
    products = [p["id"] for p in json.loads(
        urllib.request.urlopen(urllib.request.Request(f"{API}/v1/products",
            headers={"Authorization": f"Bearer {token}"})).read())]
    customers = [c["id"] for c in json.loads(
        urllib.request.urlopen(urllib.request.Request(f"{API}/v1/customers",
            headers={"Authorization": f"Bearer {token}"})).read())]

    print(f"Soko load test — {REQUESTS} requests, {WORKERS} concurrent\n")

    for label, fn in [
        ("GET /v1/overview", lambda: get("/v1/overview", token)),
        ("GET /v1/orders", lambda: get("/v1/orders?limit=50", token)),
        ("GET /v1/offers", lambda: get("/v1/offers", token)),
    ]:
        started = time.perf_counter()
        with ThreadPoolExecutor(max_workers=WORKERS) as pool:
            samples = list(pool.map(lambda _: fn(), range(REQUESTS)))
        summarise(label, samples, time.perf_counter() - started)

    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=WORKERS) as pool:
        samples = list(pool.map(
            lambda _: place(token, random.choice(customers), products), range(REQUESTS)))
    summarise("POST /v1/orders", samples, time.perf_counter() - started)
