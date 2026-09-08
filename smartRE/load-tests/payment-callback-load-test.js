// The M-Pesa callback path, under duplicate delivery.
//
// This is not really a throughput test. Safaricom retries a callback it did not see
// acknowledged, and the retry can arrive while the first is still being processed. The
// property that matters is that a repeated CheckoutRequestID posts a payment exactly
// once no matter how many times it arrives or how closely the copies overlap — because
// the failure mode is not a slow page, it is a buyer credited twice, or a viewing fee
// taken twice.
//
// So the load here is deliberately shaped as concurrent duplicates rather than distinct
// requests: a small pool of receipts, each fired repeatedly from many virtual users at
// once.
//
//   k6 run -e CALLBACK_SECRET=<the MPESA_CALLBACK_SECRET> load-tests/payment-callback-load-test.js
//
// Two things this cannot check on its own, and you must check by hand afterwards:
//   1. That the payments table holds one row per CheckoutRequestID. The endpoint returns
//      200 either way — it is a webhook, and telling Safaricom about our internal state
//      would only make it retry.
//   2. That no duplicate PAYMENT_COMPLETED reached Kafka. Read the outbox table:
//      SELECT payment_id, count(*) FROM payment_outbox_events GROUP BY 1 HAVING count(*) > 1;
//
// The endpoint also enforces an IP allow-list. Run this from a host the policy admits,
// or every response will be 403 and the run will prove nothing.

import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SECRET = __ENV.CALLBACK_SECRET || '';
// Small on purpose: fewer distinct receipts means more collisions per receipt, which is
// the condition being tested.
const RECEIPT_POOL = Number(__ENV.RECEIPT_POOL || 5);

const accepted = new Counter('callbacks_accepted');
const forbidden = new Counter('callbacks_forbidden');
const errorRate = new Rate('errors');

export const options = {
  scenarios: {
    duplicate_storm: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    // A webhook must absorb retries without falling over. Anything above this and
    // Safaricom's own timeout starts producing yet more retries.
    http_req_duration: ['p(95)<1000'],
    errors: ['rate<0.01'],
  },
};

function callbackBody(checkoutRequestId, receipt, amount) {
  return JSON.stringify({
    Body: {
      stkCallback: {
        MerchantRequestID: `load-test-${checkoutRequestId}`,
        CheckoutRequestID: checkoutRequestId,
        ResultCode: 0,
        ResultDesc: 'The service request is processed successfully.',
        CallbackMetadata: {
          Item: [
            { Name: 'Amount', Value: amount },
            { Name: 'MpesaReceiptNumber', Value: receipt },
            { Name: 'TransactionDate', Value: 20260907120000 },
            { Name: 'PhoneNumber', Value: 254700000000 },
          ],
        },
      },
    },
  });
}

export default function () {
  if (!SECRET) return;

  // Every VU draws from the same small pool, so the same CheckoutRequestID is in flight
  // from several VUs simultaneously — which is the race worth testing, not a sequence of
  // polite retries.
  const n = Math.floor(Math.random() * RECEIPT_POOL);
  const checkoutRequestId = `ws_CO_LOADTEST_${n}`;
  const receipt = `LOADTEST${n}`;

  const res = http.post(
    `${BASE_URL}/api/payments/mpesa/callback/${SECRET}`,
    callbackBody(checkoutRequestId, receipt, 500),
    { headers: { 'Content-Type': 'application/json' } },
  );

  if (res.status === 200) accepted.add(1);
  if (res.status === 403) forbidden.add(1);

  const ok = check(res, {
    // 200 means "received", not "posted" — the handler swallows its own errors on
    // purpose so Safaricom stops retrying. 403 means the secret or the source IP was
    // rejected, which is a misconfigured run rather than a system fault.
    'callback acknowledged or explicitly refused': r => r.status === 200 || r.status === 403,
    'no server error': r => r.status < 500,
  });
  errorRate.add(!ok);
}

export function handleSummary(data) {
  const lines = [];
  if (!SECRET) {
    lines.push('SKIPPED: no CALLBACK_SECRET given, so nothing was sent.');
  } else {
    const f = data.metrics.callbacks_forbidden ? data.metrics.callbacks_forbidden.values.count : 0;
    if (f > 0) {
      lines.push(
        `${f} callbacks were refused with 403. Either the secret is wrong or this host is`,
        'not on the callback IP allow-list. Nothing about idempotency was tested.',
      );
    } else {
      lines.push(
        'Now check the database — the endpoint cannot tell you this itself:',
        '  SELECT checkout_request_id, count(*) FROM payments',
        "   WHERE checkout_request_id LIKE 'ws_CO_LOADTEST_%' GROUP BY 1 HAVING count(*) > 1;",
        '  SELECT payment_id, count(*) FROM payment_outbox_events GROUP BY 1 HAVING count(*) > 1;',
        'Both must return zero rows.',
      );
    }
  }
  return { stdout: '\n' + lines.join('\n') + '\n' };
}
