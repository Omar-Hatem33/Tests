package com.team21.uber.contracts.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Reads X-Correlation-ID, X-User-Id, X-User-Role from inbound headers into MDC
 * so log lines and outbound Feign calls carry the same trace ids.
 * Generates a UUID when no correlation header is present.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HDR_CORRELATION = "X-Correlation-ID";
    public static final String HDR_USER_ID     = "X-User-Id";
    public static final String HDR_USER_ROLE   = "X-User-Role";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(HDR_CORRELATION);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        String userId   = request.getHeader(HDR_USER_ID);
        String userRole = request.getHeader(HDR_USER_ROLE);

        try {
            MDC.put("correlationId", correlationId);
            if (userId   != null) MDC.put("userId",   userId);
            if (userRole != null) MDC.put("userRole", userRole);
            response.setHeader(HDR_CORRELATION, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("userId");
            MDC.remove("userRole");
        }
    }
}
