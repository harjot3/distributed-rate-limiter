package harjot.distributed_rate_limiter.filter;

import harjot.distributed_rate_limiter.dto.RateLimitResult;
import harjot.distributed_rate_limiter.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

public class DistributedRateLimitFilter extends HttpFilter {

    private final RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        String clientKey = getClientKey(req);
        RateLimitResult result = rateLimitService.tryConsume(clientKey, 1);

        res.setHeader("X-RateLimit-Limit", String.valueOf(rateLimitService.getCapacity()));
        res.setHeader("X-RateLimit-Remaining", String.valueOf((long) result.remainingTokens()));

        if (!result.allowed()) {
            res.setHeader("Retry-After-Ms", String.valueOf(result.retryAfterMs()));
            res.setStatus(429); // Too Many Requests
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"rate limit exceeded\",\"retryAfterMs\":"
                    + result.retryAfterMs() + "}");
            return;
        }

        chain.doFilter(req, res);
    }

    private String getClientKey(HttpServletRequest req) {
        String apiKey = req.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        String forwardedFor = req.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}

