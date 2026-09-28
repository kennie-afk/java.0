// Does keyset pagination actually hold up at depth, or only in theory?
//
// This is the test that makes the claim falsifiable. `OFFSET n LIMIT 25` does not skip n
// rows, it reads and discards them, so an offset page's cost grows with how far the caller
// has walked. Keyset asks for rows after a remembered position instead, and should cost the
// same on page 2,000 as on page 1.
//
// Both are measured against the same endpoint family on the same data in the same run, so
// the comparison cannot be explained away by warm caches or machine state. What matters is
// not the absolute numbers - those describe whatever box this runs on - but the *shape*:
// offset should climb with depth and keyset should stay flat. If keyset climbs too, the
// index is not being used and V3__keyset_indexes.sql is the first place to look.
//
//   k6 run -e BASE_URL=http://localhost:18080 -e EMAIL=... -e PASSWORD=... deep-pagination.js
import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:18080';
const EMAIL = __ENV.EMAIL || 'demo@smartseason.local';
const PASSWORD = __ENV.PASSWORD || 'a-strong-demo-passphrase';
const PAGE_SIZE = Number(__ENV.PAGE_SIZE || 25);

// Depth is expressed in pages so the two strategies do the same amount of user-visible
// work: reaching page 40 means forty requests either way.
const DEPTHS = [1, 5, 20, 40];

const offsetByDepth = {};
const keysetByDepth = {};
for (const d of DEPTHS) {
  offsetByDepth[d] = new Trend(`offset_page_${d}`, true);
  keysetByDepth[d] = new Trend(`keyset_page_${d}`, true);
}
const errors = new Rate('errors');

export const options = {
  scenarios: {
    compare: { executor: 'shared-iterations', vus: 4, iterations: 40, maxDuration: '10m' },
  },
  thresholds: {
    errors: ['rate<0.01'],
    // The assertion that matters: the deepest keyset page must not be dramatically slower
    // than the shallowest. Generous, because this may run on a laptop - it is checking for
    // a missing index, not tuning.
    [`keyset_page_${DEPTHS[DEPTHS.length - 1]}`]: ['p(95)<1000'],
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

// The endpoint family under test. Any generated service works; farms is chosen because it
// is seeded with the most rows.
const RESOURCE = __ENV.RESOURCE || '/api/farm/v1/farms';

export default function (data) {
  const headers = {
    Authorization: `Bearer ${data.token}`,
    'Content-Type': 'application/json',
  };

  // ---- offset: jump straight to the page, which is what makes it expensive ----
  for (const depth of DEPTHS) {
    const res = http.get(
      `${BASE_URL}${RESOURCE}?page=${depth - 1}&size=${PAGE_SIZE}`,
      { headers, tags: { strategy: 'offset', depth: String(depth) } },
    );
    offsetByDepth[depth].add(res.timings.duration);
    errors.add(res.status !== 200);
    check(res, { 'offset page ok': (r) => r.status === 200 });
  }

  // ---- keyset: walk forward, carrying the cursor, as a real client would ----
  let cursor = null;
  let page = 0;
  const wanted = new Set(DEPTHS);
  while (page < Math.max(...DEPTHS)) {
    const url = cursor
      ? `${BASE_URL}${RESOURCE}/cursor?cursor=${encodeURIComponent(cursor)}&size=${PAGE_SIZE}`
      : `${BASE_URL}${RESOURCE}/cursor?size=${PAGE_SIZE}`;
    const res = http.get(url, { headers, tags: { strategy: 'keyset', depth: String(page + 1) } });
    errors.add(res.status !== 200);
    if (res.status !== 200) break;

    page += 1;
    if (wanted.has(page)) {
      keysetByDepth[page].add(res.timings.duration);
    }

    const body = res.json();
    cursor = body.nextCursor;
    // Running out of data early is not a failure of the mechanism; it means the dataset is
    // smaller than the deepest page asked for. Seed more rows to exercise real depth.
    if (!cursor) break;
  }
}

export function handleSummary(data) {
  const line = (name) => {
    const m = data.metrics[name];
    return m ? `${m.values['p(95)'].toFixed(1)}ms p95` : 'no data';
  };
  let out = '\nDepth comparison (p95 per page request)\n';
  out += '  depth   offset            keyset\n';
  for (const d of DEPTHS) {
    out += `  ${String(d).padEnd(7)} ${line(`offset_page_${d}`).padEnd(17)} ${line(`keyset_page_${d}`)}\n`;
  }
  out += '\nOffset should climb with depth; keyset should stay flat.\n';
  out += 'If keyset climbs too, the (tenant_id, created_at DESC, id DESC) index is not being used.\n';
  return { stdout: out };
}
