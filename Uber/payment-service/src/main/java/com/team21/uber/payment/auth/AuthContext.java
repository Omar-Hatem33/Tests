package com.team21.uber.payment.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AuthContext {

    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private String token;
    private Long userId;
    private String email;
    private String role;
    private String requiredRole;

    private int errorStatus;
    private String errorMessage;

    public AuthContext(HttpServletRequest request) {
        this.request = request;
        this.response = null;
    }

    public AuthContext(HttpServletRequest request, HttpServletResponse response) {
        this.request = request;
        this.response = response;
    }

    public HttpServletRequest getRequest()  { return request; }
    public HttpServletResponse getResponse() { return response; }

    public String getToken()               { return token; }
    public void   setToken(String token)   { this.token = token; }

    public Long   getUserId()              { return userId; }
    public void   setUserId(Long userId)   { this.userId = userId; }

    public String getEmail()               { return email; }
    public void   setEmail(String email)   { this.email = email; }

    public String getRole()                { return role; }
    public void   setRole(String role)     { this.role = role; }

    public String getRequiredRole()                    { return requiredRole; }
    public void   setRequiredRole(String requiredRole) { this.requiredRole = requiredRole; }

    public int    getErrorStatus()         { return errorStatus; }
    public String getErrorMessage()        { return errorMessage; }
    public boolean hasError()              { return errorStatus != 0; }

    public void fail(int status, String message) {
        this.errorStatus  = status;
        this.errorMessage = message;
    }
}