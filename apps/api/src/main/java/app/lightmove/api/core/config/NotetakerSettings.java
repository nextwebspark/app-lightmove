package app.lightmove.api.core.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Uncava Notetaker — {@code lightmove.notetaker.*}. {@code enabled} is the deployment's switch: off, nothing is
 * joined and no workspace is offered the setting. {@code platforms} names the calls our own bot can join, by
 * {@code NotetakerPlatform}'s spelling.
 */
public record NotetakerSettings(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("GOOGLE_MEET") List<String> platforms,
        @DefaultValue("Uncava Notetaker") String botName,
        /** How long before a call starts its bot is sent. */
        @DefaultValue("2m") Duration joinAhead,
        /** How long after a call started a bot is still sent: a pass missed by a restart still joins. */
        @DefaultValue("10m") Duration joinLate,
        /** A bot that has not reported within this of being sent never started. */
        @DefaultValue("5m") Duration launchTimeout,
        /** A bot silent for this long is gone. It beats every five seconds. */
        @DefaultValue("2m") Duration heartbeatTimeout,
        /** How long a bot waits to be admitted before it gives up. Handed to the bot. */
        @DefaultValue("10m") Duration lobbyTimeout,
        /** The longest a bot stays in a call, whatever the calendar said. Handed to the bot. */
        @DefaultValue("3h") Duration maxDuration,
        /** Spelled out in yml: {@code @Scheduled} reads it. */
        @DefaultValue("PT1M") String scheduleInterval
) {}
