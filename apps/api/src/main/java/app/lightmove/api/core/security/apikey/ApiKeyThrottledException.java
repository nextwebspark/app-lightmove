package app.lightmove.api.core.security.apikey;

import org.springframework.security.core.AuthenticationException;

/** A request over its key's or its IP's budget, refused before any database work; answered 429. */
public class ApiKeyThrottledException extends AuthenticationException {

    private final long retryAfterSeconds;

    public ApiKeyThrottledException(long retryAfterSeconds) {
        super("Too many requests");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
