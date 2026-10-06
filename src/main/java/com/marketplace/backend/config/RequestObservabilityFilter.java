package com.marketplace.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestObservabilityFilter
        extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(
                    RequestObservabilityFilter.class
            );

    public static final String REQUEST_ID_HEADER =
            "X-Request-ID";

    private static final String MDC_REQUEST_ID =
            "requestId";

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {

        String path =
                request.getRequestURI();

        return path.equals(
                "/actuator/health"
        ) ||
                path.startsWith(
                        "/actuator/health/"
                ) ||
                path.equals(
                        "/livez"
                ) ||
                path.equals(
                        "/readyz"
                );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId =
                resolveRequestId(
                        request
                );

        long startedAt =
                System.nanoTime();

        MDC.put(
                MDC_REQUEST_ID,
                requestId
        );

        response.setHeader(
                REQUEST_ID_HEADER,
                requestId
        );

        try {

            filterChain.doFilter(
                    request,
                    response
            );

        } finally {

            long durationMs =
                    (System.nanoTime() -
                            startedAt)
                            / 1_000_000;

            log.info(
                    "http_request method={} path={} status={} durationMs={} requestId={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs,
                    requestId
            );

            MDC.remove(
                    MDC_REQUEST_ID
            );
        }
    }

    private String resolveRequestId(
            HttpServletRequest request
    ) {

        String supplied =
                request.getHeader(
                        REQUEST_ID_HEADER
                );

        if (isValidRequestId(
                supplied
        )) {

            return supplied;
        }

        return UUID.randomUUID()
                .toString();
    }

    private boolean isValidRequestId(
            String value
    ) {

        if (value == null ||
                value.isBlank() ||
                value.length() > 100) {

            return false;
        }

        for (int index = 0;
             index < value.length();
             index++) {

            char character =
                    value.charAt(
                            index
                    );

            boolean valid =
                    Character.isLetterOrDigit(
                            character
                    ) ||
                            character == '-' ||
                            character == '_' ||
                            character == '.' ||
                            character == ':';

            if (!valid) {
                return false;
            }
        }

        return true;
    }
}