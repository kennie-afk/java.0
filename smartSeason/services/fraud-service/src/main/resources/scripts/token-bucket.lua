-- Atomic token bucket.
--
-- KEYS[1]   bucket key
-- ARGV[1]   capacity           (tokens)
-- ARGV[2]   refill per second  (tokens)
-- ARGV[3]   now                (milliseconds)
-- ARGV[4]   cost               (tokens)
--
-- Returns { allowed, remaining, retry_after_millis }.
--
-- Check and consume have to be one operation. Read-then-write from several
-- replicas lets two callers both see the last token and both take it, which is
-- exactly the burst the limit exists to stop.

local capacity   = tonumber(ARGV[1])
local refill     = tonumber(ARGV[2])
local now        = tonumber(ARGV[3])
local cost       = tonumber(ARGV[4])

local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'updated')
local tokens = tonumber(bucket[1])
local updated = tonumber(bucket[2])

if tokens == nil then
  tokens = capacity
  updated = now
end

-- Refill for the time that has passed, never above capacity.
local elapsed = math.max(0, now - updated) / 1000.0
tokens = math.min(capacity, tokens + elapsed * refill)

local allowed = 0
local retry_after = 0

if tokens >= cost then
  tokens = tokens - cost
  allowed = 1
else
  -- How long until one more token is worth waiting for.
  retry_after = math.ceil(((cost - tokens) / refill) * 1000)
end

redis.call('HSET', KEYS[1], 'tokens', tokens, 'updated', now)

-- Expire an idle bucket rather than keeping a key per caller for ever. Two
-- refill windows is long enough that an active caller never loses its state.
local ttl = math.ceil((capacity / refill) * 2)
redis.call('EXPIRE', KEYS[1], math.max(ttl, 60))

return { allowed, math.floor(tokens), retry_after }
