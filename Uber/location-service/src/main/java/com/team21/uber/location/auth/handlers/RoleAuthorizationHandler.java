package com.team21.uber.location.auth.handlers;

import com.team21.uber.location.auth.AuthContext;
import com.team21.uber.location.auth.AuthHandler;
import jakarta.servlet.http.HttpServletResponse;

public class RoleAuthorizationHandler extends AuthHandler {
    @Override
    protected void doHandle(AuthContext ctx) {
        if (ctx.getRequiredRole() == null) return;
        if (ctx.getRole() == null || !ctx.getRequiredRole().equalsIgnoreCase(ctx.getRole())) {
            ctx.fail(HttpServletResponse.SC_FORBIDDEN,
                    "Insufficient role: required " + ctx.getRequiredRole() + ", actual " + ctx.getRole());
        }
    }
}
