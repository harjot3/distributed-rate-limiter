import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// Custom metrics so you get real numbers for your resume/interviews instead
// of guessing: allowed vs limited request counts, and latency broken out
// separately for each so a slow limiter path can't hide in an averaged number.
const allowedCount = new Counter('requests_allowed');
const limitedCount = new Counter('requests_limited');
const allowedLatency = new Trend('latency_allowed_ms');
const limitedLatency = new Trend('latency_limited_ms');

export const options = {
  scenarios: {
    // Ramp concurrent virtual users up to simulate many distinct clients
    // hammering the service at once — this is what actually exercises the
    // "distributed" part: multiple app instances (or VUs) hitting the same
    // Redis-backed buckets concurrently.
    burst: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 50 },
        { duration: '30s', target: 200 },
        { duration: '20s', target: 200 },
        { duration: '10s', target: 0 },
      ],
    },
  },
  thresholds: {
    // Fail the run if p95 latency on ALLOWED requests blows past this —
    // this is the number you actually want to quote: "p95 latency under load."
    'latency_allowed_ms': ['p(95)<50'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // Distinct API keys per VU simulate distinct tenants each with their own bucket
  const clientId = `client-${__VU}`;
  const res = http.get(`${BASE_URL}/api/ping`, {
    headers: { 'X-API-Key': clientId },
  });

  const allowed = res.status === 200;
  check(res, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
  });

  if (allowed) {
    allowedCount.add(1);
    allowedLatency.add(res.timings.duration);
  } else {
    limitedCount.add(1);
    limitedLatency.add(res.timings.duration);
  }

  sleep(0.05);
}
