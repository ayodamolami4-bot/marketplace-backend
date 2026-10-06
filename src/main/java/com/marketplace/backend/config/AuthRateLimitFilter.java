package com.marketplace.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthRateLimitFilter
        extends OncePerRequestFilter {

    private final Map<String, WindowCounter> counters =
            new ConcurrentHashMap<>();

    private final int maxRequests;
    private final long windowMs;

    public AuthRateLimitFilter(
            @Value("${security.auth-rate-limit.max-requests:10}")
            int maxRequests,
            @Value("${security.auth-rate-limit.window-ms:60000}")
            long windowMs
    ) {

        if (maxRequests < 1) {
            throw new IllegalArgumentException(
                    "maxRequests must be at least 1"
            );
        }

        if (windowMs < 1) {
            throw new IllegalArgumentException(
                    "windowMs must be at least 1"
            );
        }

        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {

        if (!"POST".equalsIgnoreCase(
                request.getMethod()
        )) {
            return true;
        }

        String path =
                request.getRequestURI();

        return !"/api/v1/auth/login".equals(path)
                &&
                !"/api/v1/auth/signup".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        long now =
                System.currentTimeMillis();

        String clientAddress =
                request.getRemoteAddr();

        if (clientAddress == null ||
                clientAddress.isBlank()) {

            clientAddress =
                    "unknown";
        }

        String key =
                clientAddress
                        + ":"
                        + request.getRequestURI();

        WindowCounter counter =
                counters.computeIfAbsent(
                        key,
                        ignored ->
                                new WindowCounter(
                                        now
                                )
                );

        boolean allowed =
                counter.tryAcquire(
                        now,
                        maxRequests,
                        windowMs
                );

        if (!allowed) {

            /*
             * HTTP 429 = Too Many Requests.
             *
             * Using the numeric status avoids servlet-version
             * compatibility problems with SC_TOO_MANY_REQUESTS.
             */
            response.setStatus(
                    429
            );

            response.setContentType(
                    "application/json"
            );

            response.setCharacterEncoding(
                    "UTF-8"
            );

            response.setHeader(
                    "Retry-After",
                    "60"
            );

            response.getWriter()
                    .write(
                            """
                            {
                              "error": {
                                "code": "RATE_LIMIT_EXCEEDED",
                                "message": "Too many authentication attempts. Try again later."
                              }
                            }
                            """
                    );

            return;
        }

        filterChain.doFilter(
                request,
                response
        );
    }

    private static final class WindowCounter {

        private long windowStartedAt;
        private int count;

        private WindowCounter(
                long windowStartedAt
        ) {
            this.windowStartedAt =
                    windowStartedAt;

            this.count =
                    0;
        }

        private synchronized boolean tryAcquire(
                long now,
                int maxRequests,
                long windowMs
        ) {

            if (now - windowStartedAt >=
                    windowMs) {

                windowStartedAt =
                        now;

                count =
                        0;
            }

            count++;

            return count <=
                    maxRequests;
        }
    }
}