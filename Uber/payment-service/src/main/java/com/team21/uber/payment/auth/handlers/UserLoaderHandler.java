package com.team21.uber.payment.auth.handlers;

import com.team21.uber.payment.auth.AuthContext;
import com.team21.uber.payment.auth.AuthHandler;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;

public class UserLoaderHandler extends AuthHandler {

    private final JdbcTemplate jdbcTemplate;

    public UserLoaderHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doHandle(AuthContext ctx) {
        // Prefer lookup by numeric id (uid claim) — faster and unambiguous
        Long userId = ctx.getUserId();
        try {
            Integer count;
            if (userId != null) {
                count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE id = ?",
                        Integer.class,
                        userId);
            } else {
                // Fallback to email if uid claim was absent
                count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE email = ?",
                        Integer.class,
                        ctx.getEmail());
            }

            if (count == null || count == 0) {
                ctx.fail(HttpServletResponse.SC_UNAUTHORIZED, "User not found");
            }

        } catch (Exception e) {
        }
    }
}