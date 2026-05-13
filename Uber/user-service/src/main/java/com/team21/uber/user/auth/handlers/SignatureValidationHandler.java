package com.team21.uber.user.auth.handlers;

import com.team21.uber.user.auth.AuthContext;
import com.team21.uber.user.auth.AuthHandler;
import com.team21.uber.user.auth.JwtService;
import jakarta.servlet.http.HttpServletResponse;

public class SignatureValidationHandler extends AuthHandler {

    private final JwtService jwtService;

    public SignatureValidationHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doHandle(AuthContext ctx) {
        try {
            var claims = jwtService.parse(ctx.getToken());
            ctx.setEmail(claims.getSubject());
            ctx.setUserId(((Number) claims.get("uid")).longValue());
            ctx.setRole((String) claims.get("role"));
        } catch (Exception e) {
            ctx.fail(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
        }
    }
}