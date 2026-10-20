package app.lightmove.api.core.config;

import java.time.Duration;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Deleted notes — {@code lightmove.notes.*}: how long a delete can be taken back before nothing of the note is kept. */
public record NoteSettings(
        @DefaultValue("30d") Duration purgeAfter,
        /** How often notes past {@code purgeAfter} are deleted for good. Spelled out in yml: {@code @Scheduled} reads it. */
        @DefaultValue("PT1H") String purgeInterval
) {}
