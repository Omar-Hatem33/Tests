package com.team21.uber.ride.auth.handlers;

import com.team21.uber.ride.auth.AuthContext;
import com.team21.uber.ride.auth.AuthHandler;
import com.team21.uber.ride.auth.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignatureValidationHandler extends AuthHandler {

    private static final Logger log = LoggerFactory.getLogger(SignatureValidationHandler.class);

    private final JwtService jwtService;

    public SignatureValidationHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doHandle(AuthContext ctx) {
        try {
            var claims = jwtService.parse(ctx.getToken());

            Object uid = claims.get("uid");
            if (uid instanceof Number) {
                ctx.setUserId(((Number) uid).longValue());
            } else if (uid != null) {
                try { ctx.setUserId(Long.valueOf(uid.toString())); } catch (NumberFormatException ignored) {}
            }
            ctx.setEmail(claims.getSubject());
            ctx.setRole((String) claims.get("role"));

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            ctx.fail(HttpServletResponse.SC_UNAUTHORIZED, "Token expired");

        } catch (io.jsonwebtoken.security.SignatureException e) {
            ctx.fail(HttpServletResponse.SC_UNAUTHORIZED, "Invalid signature");

        } catch (io.jsonwebtoken.JwtException e) {
            ctx.fail(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");

        } catch (Exception e) {
            log.error("Token processing error: {} — {}", e.getClass().getName(), e.getMessage(), e);
            ctx.fail(HttpServletResponse.SC_UNAUTHORIZED, "Token processing error");
        }
    }
}