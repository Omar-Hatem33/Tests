package com.team21.uber.api_gateway.filter;

import com.team21.uber.api_gateway.auth.JwtValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class JwtGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtGatewayFilter.class);

    private final JwtValidator jwtValidator;

    public JwtGatewayFilter(JwtValidator jwtValidator) {

        this.jwtValidator = jwtValidator;
        log.info("✅ JwtGatewayFilter initialized"); // add this

    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;  // run before everything
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Strip inbound identity headers so clients cannot spoof identity
        ServerHttpRequest scrubbed = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove("X-User-Id");
                    h.remove("X-User-Role");
                    h.remove("X-Internal-Caller");
                })
                .build();
        ServerWebExchange scrubbedExchange = exchange.mutate().request(scrubbed).build();

        // Public routes — inject a correlation ID and pass through without JWT check
        if (path.startsWith("/api/auth/")) {
            String correlationId = resolveCorrelationId(scrubbedExchange);
            ServerHttpRequest mutated = scrubbed.mutate()
                    .header("X-Correlation-ID", correlationId)
                    .build();
            return chain.filter(scrubbedExchange.mutate().request(mutated).build());
        }

        // All other routes require a valid Bearer token
        String authHeader = scrubbed.getHeaders().getFirst("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or malformed Authorization header for path: {}", path);
            scrubbedExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return scrubbedExchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtValidator.validate(token);

            String userId        = claims.get("uid", Long.class).toString();
            String role          = claims.get("role", String.class);
            String correlationId = resolveCorrelationId(scrubbedExchange);

            ServerHttpRequest mutated = scrubbed.mutate()
                    .header("X-User-Id",        userId)
                    .header("X-User-Role",       role)
                    .header("X-Correlation-ID",  correlationId)
                    .build();

            return chain.filter(scrubbedExchange.mutate().request(mutated).build());

//        } catch (JwtException e) {
//            log.warn("Invalid JWT for path {}: {}", path, e.getMessage());
//            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
//            return exchange.getResponse().setComplete();
//        }
        } catch (JwtException e) {
            log.warn("Invalid JWT for path {}: {}", path, e.getMessage());
            log.warn("Exception type: {}", e.getClass().getName());
            scrubbedExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return scrubbedExchange.getResponse().setComplete();
        }
    }

    private String resolveCorrelationId(ServerWebExchange exchange) {
        String existing = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
        return (existing != null && !existing.isBlank()) ? existing : UUID.randomUUID().toString();
    }
}