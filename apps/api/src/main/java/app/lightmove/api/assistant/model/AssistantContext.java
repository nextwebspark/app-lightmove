package app.lightmove.api.assistant.model;

import app.lightmove.api.project.model.ProjectFacts;

/**
 * What the model is told before it answers: who is asking, and which mandate is in view.
 *
 * <p><b>Context, never authority.</b> Naming a mandate here is what lets the model pass its id to a
 * tool; whether it may is decided per call against that argument, by the guard, which re-reads the
 * membership rows every time. A thread's {@code project_id} reaching this record therefore grants
 * nothing that was not already granted.
 *
 * @param consultant   how to address the person asking, never used for a decision
 * @param mandate      the mandate the thread was started about, or null — the assistant opens on
 *                     every screen, and most of them are not a mandate
 */
public record AssistantContext(String consultant, ProjectFacts mandate) {

    public AssistantContext {
        if (consultant == null || consultant.isBlank()) {
            throw new IllegalArgumentException("a turn is asked on behalf of somebody");
        }
    }

    public boolean hasMandate() {
        return mandate != null;
    }
}
