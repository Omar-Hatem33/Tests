package com.team21.uber.payment.auth;

public abstract class AuthHandler {

    private AuthHandler next;

    public AuthHandler setNext(AuthHandler next) {
        this.next = next;
        return next;
    }

    public boolean handle(AuthContext ctx) {
        doHandle(ctx);
        if (ctx.hasError()) {
            return false;
        }
        if (next != null) {
            return next.handle(ctx);
        }
        return true;
    }

    protected abstract void doHandle(AuthContext ctx);
}