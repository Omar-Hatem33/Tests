package com.team21.uber.user.auth;

public abstract class AuthHandler {
    private AuthHandler next;

    public AuthHandler setNext(AuthHandler next) {
        this.next = next;
        return next;
    }

    public final void handle(AuthContext ctx) {
        doHandle(ctx);
        if (!ctx.hasError() && next != null) {
            next.handle(ctx);
        }
    }

    protected abstract void doHandle(AuthContext ctx);
}
