//package com.team21.uber.api_gateway.auth;
//
//import io.jsonwebtoken.Claims;
//import io.jsonwebtoken.JwtException;
//import io.jsonwebtoken.Jwts;
//import io.jsonwebtoken.security.Keys;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//
//import java.nio.charset.StandardCharsets;
//import java.util.Base64;
//
//@Component
//public class JwtValidator {
//
//    private final javax.crypto.SecretKey signingKey;
//
//    public JwtValidator(@Value("${jwt.secret}") String secret) {
//        byte[] keyBytes;
//        try {
//            // Try base64 decode first (used in Docker/production)
//            keyBytes = Base64.getDecoder().decode(secret);
//        } catch (IllegalArgumentException e) {
//            // Fall back to plain string (used in local dev)
//            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
//        }
//        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
//    }
//
//    public Claims validate(String token) throws JwtException {
//        return Jwts.parser()
//                .verifyWith(signingKey)
//                .build()
//                .parseSignedClaims(token)
//                .getPayload();
//    }
//}
package com.team21.uber.api_gateway.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class JwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);

    private final javax.crypto.SecretKey signingKey;

    public JwtValidator(@Value("${jwt.secret}") String secret) {
        log.info("JWT secret first 10 chars: {}", secret.substring(0, Math.min(10, secret.length())));
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        log.info("JWT secret loaded as raw UTF-8 bytes, length: {}", keyBytes.length);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public Claims validate(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}