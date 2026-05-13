package com.team21.uber.user.auth.dto;

import com.team21.uber.user.model.User;

public class AuthResponse {
    private String token;
    private long expiresIn;

    public AuthResponse(String token, long expiresIn) {
        this.token = token;
        this.expiresIn = expiresIn;
    }

    public String getToken() { return token; }
    public long getExpiresIn() { return expiresIn; }
}
