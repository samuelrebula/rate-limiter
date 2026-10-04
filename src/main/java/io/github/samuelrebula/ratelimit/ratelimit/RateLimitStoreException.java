package io.github.samuelrebula.ratelimit.ratelimit;

public class RateLimitStoreException extends RuntimeException {

    public RateLimitStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
