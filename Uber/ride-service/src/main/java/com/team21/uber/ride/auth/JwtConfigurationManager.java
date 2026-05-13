package com.team21.uber.ride.auth;

/**
 * Holds shared JWT configuration. Single instance across the JVM.
 * Reads from environment variables; falls back to dev defaults.
 */
public final class JwtConfigurationManager {

    private static volatile JwtConfigurationManager instance;

    private volatile String secret;
    private volatile long expirationMs;

    private JwtConfigurationManager() {
        String envSecret = System.getenv("JWT_SECRET");
        this.secret = (envSecret == null || envSecret.isBlank())
                ? "dev-only-secret-change-me-32-chars-minimum-1234567890ABCDEF"
                : envSecret;
        String envExp = System.getenv("JWT_EXPIRATION_MS");
        long parsedExp = 86_400_000L;
        if (envExp != null && !envExp.isBlank()) {
            try { parsedExp = Long.parseLong(envExp); } catch (NumberFormatException ignored) {}
        }
        this.expirationMs = parsedExp;
    }

    public static JwtConfigurationManager getInstance() {
        JwtConfigurationManager local = instance;
        if (local == null) {
            synchronized (JwtConfigurationManager.class) {
                local = instance;
                if (local == null) {
                    instance = local = new JwtConfigurationManager();
                }
            }
        }
        return local;
    }

    public static void initConfig(String secret, long expirationMs) {
        JwtConfigurationManager mgr = getInstance();   // ensure instance exists
        mgr.secret       = secret;
        mgr.expirationMs = expirationMs;
    }

    public String getSecret() { return secret; }
    public long getExpirationMs() { return expirationMs; }
}
