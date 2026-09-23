package app.lightmove.api.assistant.tool;

import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.AssistantStep;
import app.lightmove.api.assistant.model.AssistantStepEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * What the tools did during one ask: the steps they reported and the card they proposed. Each step
 * change is passed on as it happens, so the panel can show it live; the whole record is saved with
 * the answer.
 */
public class TurnRecorder {

    private final List<AssistantStep> steps = new ArrayList<>();
    private final Consumer<AssistantStepEvent> onStep;
    private AssistantProposal proposal;

    public TurnRecorder(Consumer<AssistantStepEvent> onStep) {
        this.onStep = onStep;
    }

    public int startStep(String label) {
        steps.add(new AssistantStep(label, null));
        int index = steps.size() - 1;
        onStep.accept(new AssistantStepEvent(index, label, null, false));
        return index;
    }

    public void finishStep(int index, String detail) {
        String label = steps.get(index).label();
        steps.set(index, new AssistantStep(label, detail));
        onStep.accept(new AssistantStepEvent(index, label, detail, true));
    }

    public void propose(AssistantProposal proposal) {
        this.proposal = proposal;
    }

    public List<AssistantStep> steps() {
        return List.copyOf(steps);
    }

    public AssistantProposal proposal() {
        return proposal;
    }
}
