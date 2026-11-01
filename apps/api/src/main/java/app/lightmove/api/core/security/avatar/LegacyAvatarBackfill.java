package app.lightmove.api.core.security.avatar;

import app.lightmove.api.core.config.LightMoveProperties;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Copies every provider link stored before V169 into our own storage, so it stops expiring and stops being fetched
 * from the provider by every colleague's browser. A link that will never answer again is cleared instead.
 *
 * <p>Runs on every boot until none is left, on a thread of its own: the request pool must never wait on a provider's
 * CDN, and its caller-runs overflow would otherwise put this on the boot thread. Each write re-checks that the person
 * still holds the link it read, so two instances booting together, or a sign-in racing it, write nothing twice.
 */
@Slf4j
@Component
public class LegacyAvatarBackfill {

    private static final int PAGE_SIZE = 50;

    private final JdbcTemplate jdbc;
    private final UserAvatarService avatars;
    private final boolean enabled;

    public LegacyAvatarBackfill(JdbcTemplate jdbc, UserAvatarService avatars, LightMoveProperties properties) {
        this.jdbc = jdbc;
        this.avatars = avatars;
        this.enabled = properties.avatars().backfillEnabled();
    }

    @EventListener(ApplicationReadyEvent.class)
    void startOnBoot() {
        if (!enabled) {
            return;
        }
        Thread worker = new Thread(this::runOnceQuietly, "legacy-avatar-backfill");
        worker.setDaemon(true);
        worker.start();
    }

    /** One pass over every legacy link, in id order so a link left as it was is not read twice. */
    public int runOnce() {
        int visited = 0;
        UUID after = new UUID(0, 0);
        List<LegacyLink> page;
        do {
            page = jdbc.query("""
                    SELECT id, avatar_url FROM app_lm_user
                     WHERE avatar_url LIKE 'https://%' AND id > ?
                     ORDER BY id
                     LIMIT ?""",
                    (row, index) -> new LegacyLink(row.getObject("id", UUID.class), row.getString("avatar_url")),
                    after, PAGE_SIZE);
            for (LegacyLink link : page) {
                try {
                    avatars.copyLegacyLink(link.userId(), link.url());
                } catch (RuntimeException failed) {
                    log.warn("Could not copy the legacy picture of user {}", link.userId(), failed);
                }
                after = link.userId();
                visited++;
            }
        } while (page.size() == PAGE_SIZE);
        return visited;
    }

    private void runOnceQuietly() {
        try {
            int visited = runOnce();
            if (visited > 0) {
                log.info("Copied or cleared {} provider picture links", visited);
            }
        } catch (RuntimeException failed) {
            log.warn("The legacy picture backfill stopped; the next boot carries on", failed);
        }
    }

    private record LegacyLink(UUID userId, String url) {
    }
}
