package app.lightmove.api.assistant.service;

import app.lightmove.api.assistant.constant.AssistantTurnStatus;
import app.lightmove.api.assistant.repository.AssistantTurnRepository;
import app.lightmove.api.core.config.LightMoveProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reclaims turns that will never finish.
 *
 * <p>The worker's own catch covers every failure inside a live JVM. This covers the ones outside it:
 * a Cloud Run scale-in, a rolling deploy, an OOM, or a SIGTERM that outran the executor's shutdown
 * wait. Each leaves {@code status = RUNNING} and {@code finished_at = NULL} for ever — the panel
 * reconnecting every 55s to a stream that will never speak, and V65's
 * {@code app_lm_assistant_turn_running_idx}, created for exactly this sweep, filling with rows
 * nothing can distinguish from live ones.
 *
 * <p><b>Both instances sweep and that needs no lock.</b> The cancel is conditional on the row still
 * being RUNNING, so the loser updates nothing and says nothing. Per row rather than one bulk
 * {@code UPDATE}, deliberately: a bulk update cannot publish a notification per turn, so a client
 * holding a stream on the surviving instance would never learn its turn had died.
 */
@Component
@Slf4j
public class AssistantTurnSweeper {

    /** A handful at a time. Volume is tiny, and a runaway batch should not hold a connection. */
    private static final int BATCH = 50;

    private final AssistantTurnRepository turns;
    private final AssistantTurnStore store;
    private final Duration turnTimeout;
    private final boolean enabled;

    public AssistantTurnSweeper(AssistantTurnRepository turns, AssistantTurnStore store,
                                LightMoveProperties properties) {
        this.turns = turns;
        this.store = store;
        this.turnTimeout = properties.assistant().turnTimeout();
        this.enabled = properties.assistant().sweepEnabled();
    }

    /**
     * The flag is checked here rather than on the bean, so the bean still exists when it is off: the
     * integration test that drives {@link #sweep(Instant)} directly has to be able to inject it.
     */
    @Scheduled(fixedDelayString = "${lightmove.assistant.sweep-interval:PT1M}")
    void sweepStranded() {
        if (!enabled) {
            return;
        }
        sweep(Instant.now().minus(turnTimeout));
    }

    /**
     * Cancels every turn that started before the given instant and is still RUNNING.
     *
     * <p>Public and taking its threshold so a test can back-date one row and pass a cut-off no other
     * suite's rows can match — nothing rolls back in the integration suite, so a sweep that used
     * {@code now()} would reclaim its neighbours' work.
     *
     * @return how many it reclaimed
     */
    public int sweep(Instant startedBefore) {
        List<UUID> stranded = turns.findIdsByStatusAndStartedAtBefore(
                AssistantTurnStatus.RUNNING, startedBefore, PageRequest.of(0, BATCH));
        int reclaimed = 0;
        for (UUID turnId : stranded) {
            try {
                if (store.cancelIfRunning(turnId)) {
                    reclaimed++;
                }
            } catch (RuntimeException raced) {
                // The one legitimate collision: a worker still alive on the other instance appended
                // between the read and the cancel. It must not stop the rest of the batch, and it
                // must never throw out of a scheduled method.
                log.warn("Could not reclaim assistant turn {}", turnId, raced);
            }
        }
        if (reclaimed > 0) {
            log.warn("Reclaimed {} assistant turn(s) stranded before {}", reclaimed, startedBefore);
        }
        return reclaimed;
    }
}
