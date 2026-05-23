package com.team21.uber.location.config;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignCorrelationConfig {

    @Bean
    public RequestInterceptor correlationIdInterceptor() {
        return template -> {
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                template.header("X-Correlation-ID", correlationId);
            }
            String userId = MDC.get("userId");
            if (userId != null) {
                template.header("X-User-Id", userId);
            }
            String userRole = MDC.get("userRole");
            if (userRole != null) {
                template.header("X-User-Role", userRole);
            }

            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    template.header("Authorization", authHeader);
                }
            }
        };
    }
}
