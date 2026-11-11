package app.lightmove.api.core.config;

import java.util.List;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** What counts as an acceptable signup address — {@code lightmove.email.validation.*}. */
public record EmailValidationSettings(
        /**
         * Reject addresses whose domain publishes no MX record. Cheap (one DNS lookup), and it
         * catches the overwhelmingly common case of a typo'd domain before we waste a send and
         * a bounce on it.
         */
        @DefaultValue("true") boolean mxCheckEnabled,

        /**
         * Reject consumer email providers (gmail, outlook, …) at signup. Off by default, so a consumer signup founds
         * its own workspace ({@code gmail.com} groups no colleagues); {@code EMAIL_BLOCK_PUBLIC_DOMAINS} turns it on.
         */
        @DefaultValue("false") boolean blockPublicDomains,

        /**
         * Overrides the bundled consumer-provider list entirely. Leave empty to use the bundled
         * one — which is the sane default, and what most deployments want.
         */
        @DefaultValue("") List<String> publicDomains,

        /** Added to the bundled consumer-provider list. The usual way to extend it. */
        @DefaultValue("") List<String> extraPublicDomains,

        @DefaultValue("true") boolean blockDisposableDomains,

        /** Supplements the bundled disposable list; for domains we learn about in production. */
        @DefaultValue("") List<String> extraDisposableDomains
) {}
