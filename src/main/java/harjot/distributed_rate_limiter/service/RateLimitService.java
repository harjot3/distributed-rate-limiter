package harjot.distributed_rate_limiter.service;

import java.util.Collections;
import java.util.List;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import harjot.distributed_rate_limiter.dto.RateLimitResult;

@Service
public class RateLimitService {
    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<List> tokenBucketScript;

    // capacity = max burst size, refillRate = sustained tokens/sec allowed
    @Value("${ratelimit.capacity:20}")
    private long capacity;

    @Value("${ratelimit.refill-rate:5}")
    private double refillRate;

    public RateLimitService(RedisTemplate<String, String> redisTemplate,
                             DefaultRedisScript<List> tokenBucketScript) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = tokenBucketScript;
    }

    public RateLimitResult tryConsume(String clientId, long requestedTokens) {
        String key = "ratelimit:{" + clientId + "}"; // hash tag: keeps this key on one Redis Cluster slot
        long nowMs = System.currentTimeMillis();

        @SuppressWarnings("unchecked")
        List<Object> result = redisTemplate.execute(
                tokenBucketScript,
                Collections.singletonList(key),
                String.valueOf(capacity),
                String.valueOf(refillRate),
                String.valueOf(requestedTokens),
                String.valueOf(nowMs)
        );

        boolean allowed = "1".equals(String.valueOf(result.get(0)));
        double remaining = Double.parseDouble(String.valueOf(result.get(1)));
        long retryAfterMs = Long.parseLong(String.valueOf(result.get(2)));

        return new RateLimitResult(allowed, remaining, retryAfterMs);
    }

    public long getCapacity() {
        return capacity;
    }
}
