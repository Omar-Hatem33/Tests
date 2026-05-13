package com.team21.uber.payment.auth.handlers;

import com.team21.uber.payment.auth.AuthContext;
import com.team21.uber.payment.auth.AuthHandler;
import com.team21.uber.payment.auth.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletResponse;

public class SignatureValidationHandler extends AuthHandler {

    private final JwtService jwtService;

    public SignatureValidationHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doHandle(AuthContext ctx) {
        try {
            Claims claims = jwtService.extractAllClaims(ctx.getToken());

            ctx.setEmail(claims.getSubject());

            Object uid = claims.get("uid");
            if (uid instanceof Number) {
                ctx.setUserId(((Number) uid).longValue());
            } else if (uid != null) {
                ctx.setUserId(Long.valueOf(uid.toString()));
            }

            Object roleObj = claims.get("role");
            ctx.setRole(roleObj == null ? null : roleObj.toString());

        } catch (Exception e) {
            ctx.fail(HttpServletResponse.SC_UNAUTHORIZED,
                    "Invalid or expired token: " + e.getMessage());
        }
    }
}