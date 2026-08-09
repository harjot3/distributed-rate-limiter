-- Atomic token bucket rate limiter
-- KEYS[1] = bucket key, e.g. "ratelimit:{clientId}"
-- ARGV[1] = capacity (max tokens)
-- ARGV[2] = refill_rate (tokens added per second)
-- ARGV[3] = requested (tokens this request costs, usually 1)
-- ARGV[4] = now_ms (current time in epoch millis, passed in from app so all
--           app instances agree on "now" instead of trusting Redis server clock drift)
--
-- Returns: { allowed (1/0), remaining_tokens, retry_after_ms }

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])
local now_ms = tonumber(ARGV[4])

local bucket = redis.call("HMGET", key, "tokens", "ts")
local tokens = tonumber(bucket[1])
local last_ts = tonumber(bucket[2])

if tokens == nil then
  -- first request for this client: bucket starts full
  tokens = capacity
  last_ts = now_ms
end

-- refill based on elapsed time since last touch
local elapsed_ms = math.max(0, now_ms - last_ts)
local refill = (elapsed_ms / 1000) * refill_rate
tokens = math.min(capacity, tokens + refill)

local allowed = 0
local retry_after_ms = 0

if tokens >= requested then
  tokens = tokens - requested
  allowed = 1
else
  local deficit = requested - tokens
  retry_after_ms = math.ceil((deficit / refill_rate) * 1000)
end

redis.call("HMSET", key, "tokens", tokens, "ts", now_ms)
-- expire the bucket if the client goes idle for long enough to fully refill twice over;
-- avoids leaking keys for clients that stop sending traffic
local ttl_seconds = math.ceil((capacity / refill_rate) * 2)
redis.call("EXPIRE", key, ttl_seconds)

return { allowed, tostring(tokens), retry_after_ms }
