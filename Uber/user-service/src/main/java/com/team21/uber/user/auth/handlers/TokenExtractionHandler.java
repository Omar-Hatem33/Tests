package com.team21.uber.user.auth.handlers;

import com.team21.uber.user.auth.AuthContext;
import com.team21.uber.user.auth.AuthHandler;
import jakarta.servlet.http.HttpServletResponse;

public class TokenExtractionHandler extends AuthHandler {
    @Override
    protected void doHandle(AuthContext ctx) {
        String header = ctx.getRequest().getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            ctx.fail(HttpServletResponse.SC_UNAUTHORIZED, "Missing or malformed Authorization header");
            return;
        }
        String token = header.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            ctx.fail(HttpServletResponse.SC_UNAUTHORIZED, "Empty token");
            return;
        }
        ctx.setToken(token);
    }
}
