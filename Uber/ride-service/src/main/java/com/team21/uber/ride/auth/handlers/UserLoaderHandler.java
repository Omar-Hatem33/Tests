package com.team21.uber.ride.auth.handlers;

import com.team21.uber.ride.auth.AuthContext;
import com.team21.uber.ride.auth.AuthHandler;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;

public class UserLoaderHandler extends AuthHandler {
    private final JdbcTemplate jdbc;

    public UserLoaderHandler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected void doHandle(AuthContext ctx) {
        if (ctx.getUserId() == null) {
            ctx.fail(HttpServletResponse.SC_UNAUTHORIZED, "User not found");
            return;
        }
        try {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Long.class, ctx.getUserId());
            if (count == null || count == 0) {
                ctx.fail(HttpServletResponse.SC_UNAUTHORIZED, "User not found");
            }
        }  catch (Exception e) {
        ctx.fail(HttpServletResponse.SC_UNAUTHORIZED, "User not found");

        }
    }
}
