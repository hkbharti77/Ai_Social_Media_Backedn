package com.aiplatform.security;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    @Autowired
    private RateLimitingService rateLimitingService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Skip preflight CORS requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();

        // Always bypass rate limiting for localhost (development)
        boolean isLocalhost = "127.0.0.1".equals(ip)
                || "0:0:0:0:0:0:0:1".equals(ip)
                || "::1".equals(ip)
                || "localhost".equals(ip);

        if (isLocalhost) {
            filterChain.doFilter(request, response);
            return;
        }

        // Use authenticated user identity as bucket key when available,
        // otherwise fall back to IP — prevents shared-IP false positives (e.g. office NAT)
        String principal = request.getUserPrincipal() != null
                ? "user:" + request.getUserPrincipal().getName()
                : "ip:" + ip;

        Bucket bucket = rateLimitingService.resolveBucket(principal);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(retryAfterSeconds));
            try {
                response.getWriter().write(
                    "{\"error\":\"Too many requests\",\"retryAfterSeconds\":" + retryAfterSeconds + "}"
                );
            } catch (IOException e) {
                // Ignore write errors
            }
        }
    }
}
