package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Two-factor authentication — {@code lightmove.auth.mfa.*}. */
public record MfaSettings(
        /** Off in deployments until its rollout. */
        @DefaultValue("false") boolean enabled,

        /** How long a correct password waits for its code: long enough to open the app, short for a stolen cookie. */
        @DefaultValue("5m") Duration challengeTtl,

        @DefaultValue("lm_mfa_challenge") String challengeCookieName,

        /** A person with no password turns it on only this soon after signing in: the sign-in is their proof. */
        @DefaultValue("10m") Duration recentSignIn
) {

    public static MfaSettings defaults() {
        return new MfaSettings(false, Duration.ofMinutes(5), "lm_mfa_challenge", Duration.ofMinutes(10));
    }
}
