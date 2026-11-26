package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** How outbound mail is sent, and what an address must look like — {@code lightmove.email.*}. */
public record EmailSettings(
        /**
         * {@code log} prints the message to the console; {@code resend} actually sends it.
         *
         * <p>Defaults to {@code resend} so a deployment sends real mail without an extra env var to
         * remember. The test profile and {@code application-local.yml.example} pin {@code log}, so the
         * build and a fresh clone stay runnable without a provider account.
         */
        @DefaultValue("resend") String provider,
        @DefaultValue("Uncava") String fromName,
        @DefaultValue("noreply@uncava.com") String fromAddress,
        /**
         * Privacy notices and consent requests leave from here (#1493), on a subdomain of its own. Blank means
         * {@code fromAddress}: Resend refuses a sender on a domain it has not verified, so the variable is set only
         * once the subdomain is.
         */
        String privacyFromAddress,
        ResendSettings resend,
        EmailValidationSettings validation
) {

    public EmailSettings {
        if (privacyFromAddress == null || privacyFromAddress.isBlank()) {
            privacyFromAddress = fromAddress;
        }
    }
}
