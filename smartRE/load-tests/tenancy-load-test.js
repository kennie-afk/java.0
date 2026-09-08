// Authenticated property-management load.
//
// The existing property-search test hits public, cached, read-only endpoints — the part
// of the system most likely to be fast. This one exercises the part that is not: the
// landlord's portfolio and the tenant's own view, both of which fan out across
// property-management-service, its database, and the invoice and maintenance tables that
// grow forever.
//
// It needs real tokens. Both are optional; each scenario skips itself when its token is
// absent, so this can be run for one role at a time.
//
//   k6 run -e LANDLORD_TOKEN=<jwt> -e TENANT_TOKEN=<jwt> load-tests/tenancy-load-test.js
//
// Nothing here writes. A load test that raises maintenance requests would leave hundreds
// of rows behind and change the thing it is measuring; write paths belong in a seeded
// environment you are willing to throw away, not in a script someone may point at
// staging.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LANDLORD_TOKEN = __ENV.LANDLORD_TOKEN || '';
const TENANT_TOKEN = __ENV.TENANT_TOKEN || '';

const errorRate = new Rate('errors');
const portfolioDuration = new Trend('portfolio_duration');
const invoiceDuration = new Trend('invoice_list_duration');
const maintenanceDuration = new Trend('maintenance_list_duration');
const tenancyDuration = new Trend('tenant_dashboard_duration');

export const options = {
  scenarios: {
    landlord_portfolio: {
      executor: 'ramping-vus',
      exec: 'landlordDay',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '1m', target: 60 },
        { duration: '2m', target: 60 },
        { duration: '30s', target: 0 },
      ],
    },
    tenant_dashboard: {
      executor: 'ramping-vus',
      exec: 'tenantDay',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 30 },
        { duration: '1m', target: 120 },
        { duration: '2m', target: 120 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    // Looser than the search test on purpose: these are uncached, authenticated,
    // multi-table reads. A 2s p95 here would be a good result, not a passing one.
    http_req_duration: ['p(95)<3000'],
    errors: ['rate<0.05'],
    // The tenant dashboard is the screen a person opens on a phone to check what they
    // owe. If it degrades, that is the failure users will actually report.
    tenant_dashboard_duration: ['p(95)<2500'],
  },
};

function authed(token) {
  return { headers: { Authorization: `Bearer ${token}` }, tags: { authed: 'true' } };
}

function record(res, trend, name) {
  trend.add(res.timings.duration);
  const ok = check(res, {
    [`${name} responded 200`]: r => r.status === 200,
    // A 429 here is the per-service limiter doing its job, not a failure of the
    // endpoint — but it should not happen at these rates, so it counts as an error.
    [`${name} was not throttled`]: r => r.status !== 429,
  });
  errorRate.add(!ok);
  return ok;
}

export function landlordDay() {
  if (!LANDLORD_TOKEN) return;
  const opts = authed(LANDLORD_TOKEN);

  // The order a landlord actually moves through the app: overview, then money, then
  // problems.
  record(http.get(`${BASE_URL}/api/units/portfolio-summary`, opts), portfolioDuration, 'portfolio summary');
  sleep(0.5);
  record(http.get(`${BASE_URL}/api/units/my?size=50`, opts), portfolioDuration, 'units');
  sleep(0.5);
  record(http.get(`${BASE_URL}/api/invoices/my?size=50`, opts), invoiceDuration, 'invoices');
  sleep(0.5);
  record(http.get(`${BASE_URL}/api/maintenance/my?size=50`, opts), maintenanceDuration, 'maintenance');
  sleep(1);
}

export function tenantDay() {
  if (!TENANT_TOKEN) return;
  const opts = authed(TENANT_TOKEN);

  record(http.get(`${BASE_URL}/api/leases/my-tenancy?size=20`, opts), tenancyDuration, 'my tenancy');
  sleep(0.3);
  record(http.get(`${BASE_URL}/api/invoices/my-tenancy?size=20`, opts), tenancyDuration, 'my invoices');
  sleep(0.3);
  record(http.get(`${BASE_URL}/api/maintenance/my-tenancy?size=20`, opts), tenancyDuration, 'my repairs');
  sleep(1);
}

export function handleSummary(data) {
  const skipped = [];
  if (!LANDLORD_TOKEN) skipped.push('landlord_portfolio (no LANDLORD_TOKEN)');
  if (!TENANT_TOKEN) skipped.push('tenant_dashboard (no TENANT_TOKEN)');
  const note = skipped.length
    ? `\nSKIPPED: ${skipped.join(', ')} — this run measured less than it appears to.\n`
    : '\nBoth scenarios ran.\n';
  return { stdout: note + JSON.stringify(data.metrics.http_req_duration, null, 2) + '\n' };
}
