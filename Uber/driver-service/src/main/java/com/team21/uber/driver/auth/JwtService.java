package com.team21.uber.driver.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private SecretKey signingKey() {
        String secret = JwtConfigurationManager.getInstance().getSecret();
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(Long userId, String email, String role) {
        long now = System.currentTimeMillis();
        long exp = JwtConfigurationManager.getInstance().getExpirationMs();
        return Jwts.builder()
                .subject(email)               // sub = email (per spec §5.2)
                .claim("uid", userId)          // uid = numeric User.id (required for ownership checks)
                .claim("role", role)
                .issuedAt(new Date(now))
                .expiration(new Date(now + exp))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        // uid claim holds the numeric User.id; sub holds the email
        Object uid = parse(token).get("uid");
        if (uid == null) return null;
        return ((Number) uid).longValue();
    }

    public String extractEmail(String token) {
        return parse(token).getSubject();
    }

    public String extractRole(String token) {
        Object r = parse(token).get("role");
        return r == null ? null : r.toString();
    }
}
