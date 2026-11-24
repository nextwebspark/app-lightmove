package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.core.llm.constant.PromptOutcome;
import app.lightmove.api.core.llm.model.PromptVersionRef;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * What a save on the profile screen decided about each background value the model proposed: one it changed is
 * changed, one still standing when the Background section is confirmed is kept. One per value, each against the
 * prompt version that proposed it.
 */
final class BackgroundDecisions {

    private final Map<String, PromptVersionRef> proposedBy;

    private BackgroundDecisions(Person person) {
        this.proposedBy = person.inferredBy();
    }

    /** Taken before the save writes anything. */
    static BackgroundDecisions before(Person person) {
        return new BackgroundDecisions(person);
    }

    /** Read once the save has written the person's values, and before a confirmation clears what still stands. */
    void after(Person person, boolean confirmed, BiConsumer<PromptVersionRef, PromptOutcome> decided) {
        Map<String, PromptVersionRef> stillProposedBy = person.inferredBy();
        proposedBy.forEach((field, answeredBy) -> {
            if (!stillProposedBy.containsKey(field)) {
                decided.accept(answeredBy, PromptOutcome.CHANGED);
            } else if (confirmed) {
                decided.accept(answeredBy, PromptOutcome.ACCEPTED);
            }
        });
    }
}
