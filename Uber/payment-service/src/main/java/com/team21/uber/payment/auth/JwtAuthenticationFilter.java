package com.team21.uber.payment.auth;

import com.team21.uber.payment.auth.handlers.RoleAuthorizationHandler;
import com.team21.uber.payment.auth.handlers.SignatureValidationHandler;
import com.team21.uber.payment.auth.handlers.TokenExtractionHandler;
import com.team21.uber.payment.auth.handlers.UserLoaderHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService   jwtService;
    private final JdbcTemplate jdbcTemplate;

    public JwtAuthenticationFilter(JwtService jwtService, JdbcTemplate jdbcTemplate) {
        this.jwtService   = jwtService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/api/payments/health")
                || path.startsWith("/actuator")
                || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {

        // Build the Chain of Responsibility (DP-3)
        AuthHandler head = new TokenExtractionHandler();
        head.setNext(new SignatureValidationHandler(jwtService))
                .setNext(new UserLoaderHandler(jdbcTemplate))
                .setNext(new RoleAuthorizationHandler());

        AuthContext ctx = new AuthContext(request, response);
        boolean success = head.handle(ctx);

        if (!success) {
            log.debug("Auth chain failed [{} ]: {}", ctx.getErrorStatus(), ctx.getErrorMessage());
            response.setStatus(ctx.getErrorStatus());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + ctx.getErrorMessage() + "\"}");
            return;
        }

        String role = ctx.getRole() == null ? "RIDER" : ctx.getRole();
        log.debug("Auth OK: userId={} role={}", ctx.getUserId(), role);

        var authToken = new UsernamePasswordAuthenticationToken(
                ctx.getUserId(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContextHolder.getContext().setAuthentication(authToken);
        filterChain.doFilter(request, response);
    }
}