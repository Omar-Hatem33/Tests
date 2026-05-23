package com.team21.uber.ride.auth;

import com.team21.uber.ride.auth.handlers.RoleAuthorizationHandler;
import com.team21.uber.ride.auth.handlers.SignatureValidationHandler;
import com.team21.uber.ride.auth.handlers.TokenExtractionHandler;
import com.team21.uber.ride.auth.handlers.UserLoaderHandler;
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
                || path.endsWith("/health")
                || path.matches("/api/rides/(user|driver)/\\d+/(active-count|completed-count|summary)");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

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