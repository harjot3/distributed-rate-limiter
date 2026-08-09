# Distributed Rate Limiter

A rate limiter that sits in front of a Spring Boot API and enforces per-client
request limits using a token bucket algorithm, backed by Redis so the limit
holds correctly across multiple application instances — not just one.

## Why this exists

Every public API: Stripe, GitHub, AWS, your bank's API, has to answer the
same question: *how many requests will I let this client make per second?*
Get it wrong and buggy client can damage your backend or users will get suffocated.

The hard part to this isn't the algorithm, but making the limit hold when your
service isn't a single process. Once you're running 3 replicas behind a load
balancer, each instance only sees a third of any given client's traffic. If
each instance counts requests in its own memory, a client can get 3x their
actual limit just by getting lucky with load-balancer routing. This project
solves that by moving the counting into Redis, shared by every instance, and
using a Lua script so the "check the count, then update it" step happens as
one atomic operation; otherwise two instances checking at the exact same
millisecond could both approve a request that should have been the one over
the limit.

## Architecture

```
Client → RateLimitFilter (Spring Boot) → RateLimitService → Redis (Lua script)
                  │                                              │
                  └── 429 if denied ──────────── atomic check-and-decrement
```

- **Token bucket algorithm** — each client gets a bucket of tokens that
  refills continuously over time (rather than resetting all at once on a
  fixed schedule). This allows short bursts while still enforcing a
  sustained average rate, which is closer to how real API rate limits behave
  (e.g. GitHub's model) than a naive fixed-window counter.
- **Redis + Lua for atomicity** — the refill/check/decrement logic runs
  as a single atomic script on the Redis server, so it's safe under
  concurrent requests from multiple app instances without needing a separate
  distributed lock.
- **Spring Boot servlet filter** — the limiter runs as a filter ahead of
  any controller, so it applies uniformly across all endpoints without
  each one needing to opt in.
- **Load tested with k6** — a load-testing script simulates hundreds of
  concurrent clients to verify the limiter behaves correctly (no
  over-admission) under real concurrency, and to measure latency overhead
  under load.

## Tech stack

Java 17, Spring Boot 3, Redis, Lua, k6 (load testing), Maven

## Running it

```bash
docker run -p 6379:6379 redis:7-alpine   # start Redis
mvn spring-boot:run                       # start the app
k6 run k6/load_test.js                    # run the load test
```

## What I'd build next continuing the project

- Sliding-window log variant for comparison against token bucket
- Explicit fail-open/fail-closed handling for Redis outages
- Per-endpoint (not just per-client) limit tiers
- Prometheus metrics export for allowed/denied request rates
