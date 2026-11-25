package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.constant.PromptOutcome;
import app.lightmove.api.core.llm.model.PromptVersionRef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Adds one decision to its version's row of the day, off the calling thread; a write that fails is logged and lost. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptOutcomeWriter {

    private final PromptMonitoringStore store;

    @Async
    public void add(PromptVersionRef answeredBy, PromptOutcome outcome) {
        try {
            store.add(answeredBy, outcome);
        } catch (RuntimeException failure) {
            log.error("Could not count what a person did with an answer of prompt {} version {}",
                    answeredBy.promptId(), answeredBy.version(), failure);
        }
    }
}
