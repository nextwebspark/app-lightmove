package app.lightmove.api.prompteval.service;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/**
 * One thread that takes waiting jobs one at a time, each claimed from its store so two instances never work one.
 * Never the shared {@code @Async} pool, which runs a task on its publisher's thread once it is full: a job here
 * takes minutes.
 */
@Slf4j
final class ClaimedJobDrainer<J> {

    private final String name;
    private final Supplier<Optional<J>> claim;
    private final Consumer<J> work;
    private final BooleanSupplier isOneWaiting;
    private final ExecutorService thread;
    private final AtomicBoolean working = new AtomicBoolean();
    private volatile boolean shuttingDown;

    ClaimedJobDrainer(String name, Supplier<Optional<J>> claim, Consumer<J> work, BooleanSupplier isOneWaiting) {
        this.name = name;
        this.claim = claim;
        this.work = work;
        this.isOneWaiting = isOneWaiting;
        this.thread = Executors.newSingleThreadExecutor(Thread.ofPlatform().name(name).daemon(true).factory());
    }

    /** Hands the look for a waiting job to the drainer's thread and returns: a caller's thread is never kept. */
    void wake() {
        if (working.compareAndSet(false, true)) {
            thread.execute(this::drain);
        }
    }

    boolean isIdle() {
        return !working.get();
    }

    boolean isShuttingDown() {
        return shuttingDown;
    }

    void stop() {
        shuttingDown = true;
        thread.shutdownNow();
    }

    /**
     * Looks again after standing down: a job queued while the last one was ending found the drainer busy, and would
     * otherwise wait for the timer.
     */
    private void drain() {
        boolean lookAgain = true;
        while (lookAgain) {
            try {
                Optional<J> claimed = claim.get();
                while (claimed.isPresent()) {
                    work.accept(claimed.get());
                    claimed = claim.get();
                }
            } catch (RuntimeException failure) {
                log.error("The {} worker could not look for a job; the timer looks again", name, failure);
            } finally {
                working.set(false);
            }
            lookAgain = isAnotherWaiting() && working.compareAndSet(false, true);
        }
    }

    private boolean isAnotherWaiting() {
        try {
            return isOneWaiting.getAsBoolean();
        } catch (RuntimeException failure) {
            return false;
        }
    }
}
