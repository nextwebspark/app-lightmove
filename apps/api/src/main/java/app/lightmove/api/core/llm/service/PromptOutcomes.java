package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.constant.PromptOutcome;
import app.lightmove.api.core.llm.model.PromptVersionRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Where a feature says what a person did with an AI answer: kept as written, or changed. Counted against the
 * version that gave it, and only once the decision has committed. Never fails the caller, and takes no content.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptOutcomes {

    private final PromptOutcomeWriter writer;

    /** {@code answeredBy} null: the answer had no version (the bundled file, or a value older than the count). */
    public void record(PromptVersionRef answeredBy, PromptOutcome outcome) {
        if (answeredBy == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            add(answeredBy, outcome);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                add(answeredBy, outcome);
            }
        });
    }

    // The writer catches its own failure on its own thread; this one is the hand-off to that thread being refused,
    // which thrown out of afterCommit would fail a request whose change has already committed.
    private void add(PromptVersionRef answeredBy, PromptOutcome outcome) {
        try {
            writer.add(answeredBy, outcome);
        } catch (RuntimeException failure) {
            log.error("Could not count what a person did with an answer of prompt {}", answeredBy.promptId(), failure);
        }
    }
}
