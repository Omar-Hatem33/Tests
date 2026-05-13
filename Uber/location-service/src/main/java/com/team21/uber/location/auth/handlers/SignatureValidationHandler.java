package com.team21.uber.location.auth.handlers;

import com.team21.uber.location.auth.AuthContext;
import com.team21.uber.location.auth.AuthHandler;
import com.team21.uber.location.auth.JwtService;
import jakarta.servlet.http.HttpServletResponse;

public class SignatureValidationHandler extends AuthHandler {
    private final JwtService jwtService;

    public SignatureValidationHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doHandle(AuthContext ctx) {
        try {
            var claims = jwtService.extractAllClaims(ctx.getToken());
            Object uid = claims.get("uid");
            if (uid == null) uid = claims.get("userId");
            if (uid == null) uid = claims.get("id");
            Long userId = null;
            if (uid instanceof Number) {
                userId = ((Number) uid).longValue();
            } else if (uid != null) {
                try { userId = Long.valueOf(uid.toString()); } catch (NumberFormatException ignored) {}
            }
            String sub = claims.getSubject();
            if (userId == null && sub != null) {
                try { userId = Long.valueOf(sub); } catch (NumberFormatException ignored) {}
            }
            ctx.setUserId(userId);
            String emailClaim = (String) claims.get("email");
            ctx.setEmail(emailClaim != null ? emailClaim : sub);
            Object roleObj = claims.get("role");
            ctx.setRole(roleObj == null ? null : roleObj.toString());
        } catch (Exception e) {
            ctx.fail(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
        }
    }
}
