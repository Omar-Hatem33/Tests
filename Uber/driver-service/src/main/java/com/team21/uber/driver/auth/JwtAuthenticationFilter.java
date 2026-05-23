package com.team21.uber.driver.auth;

import com.team21.uber.driver.auth.handlers.RoleAuthorizationHandler;
import com.team21.uber.driver.auth.handlers.SignatureValidationHandler;
import com.team21.uber.driver.auth.handlers.TokenExtractionHandler;
import com.team21.uber.driver.auth.handlers.UserLoaderHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

import javax.sql.DataSource;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final DataSource dataSource;

    public JwtAuthenticationFilter(JwtService jwtService, DataSource dataSource) {
        this.jwtService = jwtService;
        this.dataSource = dataSource;
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

        AuthContext ctx = new AuthContext(request);

        // Build the handler chain
        AuthHandler head = new TokenExtractionHandler();
        head.setNext(new SignatureValidationHandler(jwtService))
                .setNext(new UserLoaderHandler(dataSource))
                .setNext(new RoleAuthorizationHandler());

        head.handle(ctx);

        // If any handler failed, write the error response and stop
        if (ctx.hasError()) {
            if (!response.isCommitted()) {
                response.setStatus(ctx.getErrorStatus());
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"" + ctx.getErrorMessage() + "\"}");
                response.getWriter().flush();
            }
            return;
        }

        // All handlers passed — populate Spring Security context
        String role = ctx.getRole() == null ? "RIDER" : ctx.getRole();
        var auth = new UsernamePasswordAuthenticationToken(
                ctx.getUserId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        SecurityContextHolder.getContext().setAuthentication(auth);

        // M3: inject X-User-Id from JWT so controllers can read caller identity
        // (mirrors what the API gateway does when deployed)
        final Long userId = ctx.getUserId();
        if (userId != null) {
            HttpServletRequest mutatedRequest = new jakarta.servlet.http.HttpServletRequestWrapper(request) {
                @Override
                public String getHeader(String name) {
                    if ("X-User-Id".equalsIgnoreCase(name)) {
                        return userId.toString();
                    }
                    return super.getHeader(name);
                }

                @Override
                public java.util.Enumeration<String> getHeaders(String name) {
                    if ("X-User-Id".equalsIgnoreCase(name)) {
                        return java.util.Collections.enumeration(
                                java.util.Collections.singletonList(userId.toString()));
                    }
                    return super.getHeaders(name);
                }
            };
            filterChain.doFilter(mutatedRequest, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }
}