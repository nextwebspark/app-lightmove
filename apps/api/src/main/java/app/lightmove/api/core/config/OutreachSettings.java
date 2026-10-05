package app.lightmove.api.core.config;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Outreach email from a consultant's own mailbox — {@code lightmove.outreach.*}. */
public record OutreachSettings(
        NylasSettings nylas,

        /** Uncava's own OAuth apps, which a workspace on the shared mode connects through. */
        ProviderAppsSettings providers,

        /** How long a started mailbox connection may take to come back from the provider's consent screen. */
        @DefaultValue("10m") Duration connectWindow,

        /** Emails one mailbox sends a day, whatever its sequences ask for: a mailbox that bursts is a mailbox flagged. */
        @DefaultValue("50") int dailyCap,

        /** The sender's working day, read in their mailbox's time zone: nothing goes outside it. */
        @DefaultValue("08:00") LocalTime windowStart,
        @DefaultValue("18:00") LocalTime windowEnd,

        /** The working week. A sequence's delays count these days only. */
        @DefaultValue("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY") List<DayOfWeek> workingDays,

        /** Enrollments one dispatch claims; the rest wait a minute for the next. */
        @DefaultValue("25") int dispatchBatch,

        /** A claim older than this is a send whose dispatcher died mid-flight: stopped, never resent. */
        @DefaultValue("10m") Duration claimTimeout,

        /** How far back the reply poll looks for threads it still listens to. */
        @DefaultValue("30d") Duration replyListenWindow,

        /**
         * Which gateway a new mailbox connects through: {@code nylas}, or {@code direct} for our own wherever it
         * covers the provider. A connection already made stays with the gateway that made it.
         */
        @DefaultValue("nylas") OutreachGateway gateway
) {}
