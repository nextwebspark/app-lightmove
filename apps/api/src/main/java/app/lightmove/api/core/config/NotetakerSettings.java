package app.lightmove.api.core.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Uncava Notetaker — {@code lightmove.notetaker.*}. {@code enabled} is the deployment's switch: off, nothing is
 * joined and no workspace is offered the setting. {@code platforms} names the calls our own bot can join, by
 * {@code NotetakerPlatform}'s spelling. {@code runner} is how a bot is started: {@code log} (nothing starts),
 * {@code local} (a process on this machine, for {@code npm run dev}) or {@code cloud-run-job} (one execution of
 * {@code cloudRunJob} per call). Where Recall.ai is configured it stands behind our bot (#955): {@code fallback} turns
 * that off. {@code fallbackOnNotAdmitted} and {@code fallbackOnJoinBlocked} let a bot nobody admitted, or one the call's
 * host or tenant keeps out, be followed by Recall's; both off by default, since each is a decision someone made.
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
        @DefaultValue("PT1M") String scheduleInterval,
        @DefaultValue("log") NotetakerRunner runner,
        /** Where the bot calls back; blank is {@code lightmove.web.base-url}. */
        String apiBaseUrl,
        /** {@code projects/<project>/locations/<region>/jobs/<job>}, for the {@code cloud-run-job} runner. */
        String cloudRunJob,
        /** The command the {@code local} runner starts, run from the repository root. */
        @DefaultValue("node apps/meetingbot/dist/main.js") String localCommand,
        /** One captured piece, at most: 30 seconds of Opus audio is about 120 KB. */
        @DefaultValue("8388608") long maxMediaBytes,
        /** How long a failed run's pieces are kept to look into before they are deleted. */
        @DefaultValue("7d") Duration failedMediaRetention,
        /** How often a call's audio is transcribed while it goes, so its note comes soon after it ends. */
        @DefaultValue("5m") Duration transcribeEvery,
        @DefaultValue("true") boolean fallback,
        @DefaultValue("false") boolean fallbackOnNotAdmitted,
        @DefaultValue("false") boolean fallbackOnJoinBlocked,
        /** A backup bot still unfinished this long past its call's end is taken as lost. */
        @DefaultValue("3h") Duration backupTimeout,
        /**
         * The bot's own service account ({@code MEETINGBOT_SA}). Set, a Cloud Run bot is started without its run token
         * and exchanges a Google ID token of this account for it (#956); blank, the token rides the environment.
         */
        String botServiceAccount,
        /** The audience that ID token is minted for; blank is the API's base URL the bot calls. */
        String botTokenAudience
) {}
