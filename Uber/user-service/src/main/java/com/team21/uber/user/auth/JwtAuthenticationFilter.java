package com.team21.uber.user.auth;

import com.team21.uber.user.auth.handlers.RoleAuthorizationHandler;
import com.team21.uber.user.auth.handlers.SignatureValidationHandler;
import com.team21.uber.user.auth.handlers.TokenExtractionHandler;
import com.team21.uber.user.auth.handlers.UserLoaderHandler;
import com.team21.uber.user.repository.UserRepository;
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

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
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

        AuthHandler head = new TokenExtractionHandler();
        head.setNext(new SignatureValidationHandler(jwtService))
                .setNext(new UserLoaderHandler(userRepository))
                .setNext(new RoleAuthorizationHandler());

        head.handle(ctx);

        if (ctx.hasError()) {
            response.setStatus(ctx.getErrorStatus());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + ctx.getErrorMessage() + "\"}");
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                ctx.getUserId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + (ctx.getRole() == null ? "RIDER" : ctx.getRole()))));
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }
}