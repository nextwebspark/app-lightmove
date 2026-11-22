package app.lightmove.api.core.llm.service;

import app.lightmove.api.core.llm.model.PromptCallCount;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Adds one call to its version's row of the day, off the calling thread. Its own bean because {@code @Async} is
 * inert on a self-call; a write that fails is logged and lost, never the caller's problem.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptCallCountWriter {

    private final PromptRegistryStore store;

    @Async
    public void add(PromptCallCount count) {
        try {
            store.add(count);
        } catch (RuntimeException failure) {
            log.error("Could not count a call of prompt {} version {}", count.promptId(), count.version(), failure);
        }
    }
}
