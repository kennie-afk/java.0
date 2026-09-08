-- Atomic token bucket.
--
-- Runs entirely inside Redis so that check-and-consume cannot interleave between
-- replicas: two pods serving the same user share one bucket, which is the whole point of
-- putting this in Redis rather than in each JVM's heap.
--
-- KEYS[1] bucket key
-- ARGV[1] capacity (max burst)
-- ARGV[2] refill rate, tokens per second
-- ARGV[3] now, epoch millis
-- ARGV[4] tokens requested
--
-- Returns { allowed (1|0), tokens remaining, retry-after seconds }

local key      = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill   = tonumber(ARGV[2])
local now      = tonumber(ARGV[3])
local wanted   = tonumber(ARGV[4])

local stored = redis.call('HMGET', key, 'tokens', 'ts')
local tokens = tonumber(stored[1])
local ts     = tonumber(stored[2])

-- An unseen key starts full, so a first request is never throttled.
if tokens == nil or ts == nil then
  tokens = capacity
  ts = now
end

-- Refill for the time elapsed. Clamped at zero because a clock that moves backwards
-- (NTP correction, or two pods disagreeing) must not remove tokens.
local elapsed = math.max(0, now - ts) / 1000.0
tokens = math.min(capacity, tokens + elapsed * refill)

local allowed = 0
if tokens >= wanted then
  tokens = tokens - wanted
  allowed = 1
end

redis.call('HSET', key, 'tokens', tokens, 'ts', now)
-- Live only as long as it takes to refill from empty. An idle key would refill to
-- capacity anyway, so keeping it costs memory and buys nothing.
redis.call('EXPIRE', key, math.ceil(capacity / refill) + 60)

local retry = 0
if allowed == 0 then
  retry = math.ceil((wanted - tokens) / refill)
end

return { allowed, math.floor(tokens), retry }
