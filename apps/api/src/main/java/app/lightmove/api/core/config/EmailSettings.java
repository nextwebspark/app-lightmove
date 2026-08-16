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
        @DefaultValue("LightMove") String fromName,
        @DefaultValue("noreply@lightmove.ai") String fromAddress,
        ResendSettings resend,
        EmailValidationSettings validation
) {}
