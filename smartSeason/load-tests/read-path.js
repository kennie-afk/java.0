// The read path across several services under a ramp.
//
// Reads are most of the traffic and the primary is where they all land today, so this is
// the test that finds the ceiling. It deliberately spreads across services rather than
// hammering one: 27 services behind one PgBouncer and one Postgres share a connection
// budget, and a single-service test would never show that contention.
//
// What to watch, in order of what it tells you:
//   - p95 rising while throughput stays flat  -> a queue somewhere, usually connections
//   - 429s                                    -> the rate limiter working, not a failure
//   - 503 upstream-unavailable                -> circuit breakers opened, look downstream
//   - errors climbing with VUs                -> the real ceiling, and where to look next
//
//   k6 run -e BASE_URL=http://localhost:18080 read-path.js
import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';
const EMAIL = __ENV.EMAIL || 'demo@smartseason.local';
const PASSWORD = __ENV.PASSWORD || 'a-strong-demo-passphrase';

const errors = new Rate('errors');
const rateLimited = new Counter('rate_limited_429');
const circuitOpen = new Counter('circuit_open_503');
const readDuration = new Trend('read_duration', true);

// Spread across bounded contexts so the shared data tier is actually exercised.
const ENDPOINTS = [
  '/api/farm/v1/farms',
  '/api/season/v1/seasons',
  '/api/inventory/v1/inventory-items',
  '/api/task/v1/tasks',
  '/api/order/v1/orders',
  '/api/workforce/v1/workers',
];

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 25 },
        { duration: '1m', target: 100 },
        { duration: '2m', target: 100 },
        { duration: '1m', target: 250 },
        { duration: '2m', target: 250 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    // Deliberately loose. This is a ceiling-finding run, not a pass/fail gate; a tight
    // threshold here would only encourage running it somewhere flattering.
    http_req_duration: ['p(95)<3000'],
    errors: ['rate<0.05'],
  },
};

export function setup() {
  const res = http.post(
    `${BASE_URL}/api/identity/v1/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (res.status !== 200) {
    throw new Error(`login failed: ${res.status} ${res.body}`);
  }
  const body = res.json();
  return { token: body.accessToken || body.token };
}

export default function (data) {
  const endpoint = ENDPOINTS[Math.floor(Math.random() * ENDPOINTS.length)];
  const res = http.get(`${BASE_URL}${endpoint}?page=0&size=25`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { endpoint },
  });

  readDuration.add(res.timings.duration);

  // A 429 is the limiter doing its job and a 503 is a breaker doing its job. Counting them
  // separately keeps them out of the error rate, where they would look like faults and
  // hide the ones that are.
  if (res.status === 429) {
    rateLimited.add(1);
  } else if (res.status === 503) {
    circuitOpen.add(1);
  } else {
    errors.add(res.status !== 200);
    check(res, { 'read ok': (r) => r.status === 200 });
  }
}
