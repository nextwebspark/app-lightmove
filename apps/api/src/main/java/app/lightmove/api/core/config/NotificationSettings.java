package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** The bell's email and its scans — {@code lightmove.notifications.*}. The crons are UTC. */
public record NotificationSettings(
        /** How long an assignment or a mention may sit unread before it is emailed. */
        @DefaultValue("10m") Duration emailDelay,
        /** How often unread rows past their delay are looked for. Spelled out in yml: {@code @Scheduled} reads it. */
        @DefaultValue("0 * * * * *") String emailScan,
        /** Hourly, so each person's 08:00 is met in their own zone. */
        @DefaultValue("0 0 * * * *") String digestScan,
        @DefaultValue("8") int digestHour,
        /** How often open tasks are checked for falling due. */
        @DefaultValue("0 */5 * * * *") String dueScan,
        /** How far ahead a task counts as due soon. */
        @DefaultValue("1h") Duration dueSoonWindow
) {}
