package com.team21.uber.driver.auth.handlers;

import com.team21.uber.driver.auth.AuthContext;
import com.team21.uber.driver.auth.AuthHandler;
import com.team21.uber.driver.auth.JwtService;
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

            // sub = email, uid = numeric User.id (per spec §5.2)
            ctx.setEmail(claims.getSubject());

            Object uid = claims.get("uid");
            if (uid != null) {
                ctx.setUserId(((Number) uid).longValue());
            }

            Object role = claims.get("role");
            ctx.setRole(role == null ? null : role.toString());

        } catch (Exception e) {
            ctx.fail(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
        }
    }
}
