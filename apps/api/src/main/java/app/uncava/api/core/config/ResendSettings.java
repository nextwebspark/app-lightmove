package app.uncava.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** Credentials for the Resend mail provider — {@code uncava.email.resend.*}. */
public record ResendSettings(
        String apiKey,
        @DefaultValue("https://api.resend.com") String baseUrl
) {}
