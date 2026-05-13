package com.team21.uber.payment.auth.handlers;

import com.team21.uber.payment.auth.AuthContext;
import com.team21.uber.payment.auth.AuthHandler;
import jakarta.servlet.http.HttpServletResponse;

public class RoleAuthorizationHandler extends AuthHandler {

    @Override
    protected void doHandle(AuthContext ctx) {
        String required = ctx.getRequiredRole();

        // No role restriction on this endpoint — pass through
        if (required == null || required.isBlank()) {
            return;
        }

        String actual = ctx.getRole();
        if (actual == null || !required.equalsIgnoreCase(actual)) {
            ctx.fail(HttpServletResponse.SC_FORBIDDEN,
                    "Insufficient role: required " + required
                            + ", actual " + (actual == null ? "none" : actual));
        }
    }
}