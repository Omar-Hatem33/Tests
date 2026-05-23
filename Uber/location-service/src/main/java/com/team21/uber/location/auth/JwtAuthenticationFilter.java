package com.team21.uber.location.auth;

import com.team21.uber.location.auth.handlers.RoleAuthorizationHandler;
import com.team21.uber.location.auth.handlers.SignatureValidationHandler;
import com.team21.uber.location.auth.handlers.TokenExtractionHandler;
import com.team21.uber.location.auth.handlers.UserLoaderHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final JdbcTemplate jdbc;

    public JwtAuthenticationFilter(JwtService jwtService, JdbcTemplate jdbc) {
        this.jwtService = jwtService;
        this.jdbc = jdbc;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/api/auth/")
                || path.startsWith("/actuator/")
                || path.endsWith("/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // When the gateway has already validated a JWT it strips spoofed X-User-* on inbound
        // and re-injects them itself. Inter-service Feign calls also forward these headers
        // from the upstream MDC. In both cases we trust them and skip the JWT chain.
        String xUserId   = request.getHeader("X-User-Id");
        String xUserRole = request.getHeader("X-User-Role");
        if (xUserId != null && !xUserId.isBlank()) {
            try {
                Long uid = Long.valueOf(xUserId);
                String roleHdr = (xUserRole == null || xUserRole.isBlank()) ? "RIDER" : xUserRole;
                var injected = new UsernamePasswordAuthenticationToken(
                        uid, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + roleHdr)));
                SecurityContextHolder.getContext().setAuthentication(injected);
                filterChain.doFilter(request, response);
                return;
            } catch (NumberFormatException ignored) {
                // fall through to the JWT path below
            }
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || authHeader.isBlank()) {
            writeUnauthorized(response, "Missing Authorization header");
            return;
        }

        AuthContext ctx = new AuthContext(request);

        AuthHandler head = new TokenExtractionHandler();
        head.setNext(new SignatureValidationHandler(jwtService))
                .setNext(new UserLoaderHandler(jdbc))
                .setNext(new RoleAuthorizationHandler());

        try {
            head.handle(ctx);
        } catch (Exception ex) {
            writeUnauthorized(response, "Invalid token");
            return;
        }

        if (ctx.hasError()) {
            writeUnauthorized(response, ctx.getErrorMessage());
            return;
        }

        String role = ctx.getRole() == null ? "RIDER" : ctx.getRole();

        var auth = new UsernamePasswordAuthenticationToken(
                ctx.getUserId(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );

        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + escapeJson(message) + "\"}");
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "Unauthorized";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}