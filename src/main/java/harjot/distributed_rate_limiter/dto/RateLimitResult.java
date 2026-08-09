package harjot.distributed_rate_limiter.dto;

public record RateLimitResult(boolean allowed, double remainingTokens, long retryAfterMs) {
}
