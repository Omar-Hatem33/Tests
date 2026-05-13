package com.team21.uber.payment.auth.handlers;

import com.team21.uber.payment.auth.AuthContext;
import com.team21.uber.payment.auth.AuthHandler;
import jakarta.servlet.http.HttpServletResponse;


public class TokenExtractionHandler extends AuthHandler {

    @Override
    protected void doHandle(AuthContext ctx) {
        String header = ctx.getRequest().getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            ctx.fail(HttpServletResponse.SC_UNAUTHORIZED,
                    "Missing or malformed Authorization header");
            return;
        }

        String token = header.substring(7).trim();
        if (token.isBlank()) {
            ctx.fail(HttpServletResponse.SC_UNAUTHORIZED, "Empty bearer token");
            return;
        }

        ctx.setToken(token);
    }
}