package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** The human check on the anonymous auth forms (Cloudflare Turnstile) — {@code lightmove.auth.human-check.*}. */
public record HumanCheckSettings(
        @DefaultValue("false") boolean enabled,

        /** Public: the SPA renders the widget with it, read from {@code /auth/providers} at runtime. */
        String siteKey,
        String secretKey,
        @DefaultValue("https://challenges.cloudflare.com/turnstile/v0/siteverify") String verifyUrl,

        /** Past it the check fails open: a vendor's bad minute must not stop everyone signing in. */
        @DefaultValue("3s") Duration timeout,

        /**
         * Refuses an answer for another host or another form, so a token solved elsewhere cannot be spent here. Off
         * only for Cloudflare's test keys, whose answers name neither.
         */
        @DefaultValue("true") boolean verifyHostnameAndAction
) {

    public static HumanCheckSettings disabled() {
        return new HumanCheckSettings(false, null, null,
                "https://challenges.cloudflare.com/turnstile/v0/siteverify", Duration.ofSeconds(3), true);
    }
}
