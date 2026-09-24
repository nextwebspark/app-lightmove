package app.lightmove.api.assistant.tool;

import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.AssistantStep;
import app.lightmove.api.assistant.model.AssistantStepEvent;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * What the tools did during one ask: the steps they reported, the companies they found or researched
 * by name, and the card they proposed. Each step
 * change is passed on as it happens, so the panel can show it live; the whole record is saved with
 * the answer.
 */
public class TurnRecorder {

    private final List<AssistantStep> steps = new ArrayList<>();
    private final Consumer<AssistantStepEvent> onStep;
    private final Consumer<AssistantProposal> onProposal;
    private final Set<String> foundAccountIds = new LinkedHashSet<>();
    private final Map<String, CapturedCompanyDetails> researched = new LinkedHashMap<>();
    private final Map<String, String> operatedBrands = new LinkedHashMap<>();
    private boolean namesLookedUp;
    private AssistantProposal proposal;

    public TurnRecorder(Consumer<AssistantStepEvent> onStep) {
        this(onStep, proposal -> { });
    }

    /** {@code onProposal} hears the card the moment it is made, before the model writes its answer. */
    public TurnRecorder(Consumer<AssistantStepEvent> onStep, Consumer<AssistantProposal> onProposal) {
        this.onStep = onStep;
        this.onProposal = onProposal;
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

    /** True the first time only: an answer looks names up once, because every lookup is billed. */
    public boolean startNameLookup() {
        boolean first = !namesLookedUp;
        namesLookedUp = true;
        return first;
    }

    public void found(Collection<String> apolloAccountIds) {
        foundAccountIds.addAll(apolloAccountIds);
    }

    public List<String> foundAccountIds() {
        return List.copyOf(foundAccountIds);
    }

    public void researched(String linkedinSlug, CapturedCompanyDetails details) {
        researched.put(linkedinSlug, details);
    }

    public Map<String, CapturedCompanyDetails> researched() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(researched));
    }

    /** Records that the company under {@code companyKey} runs {@code brand} locally, as a franchise partner does. */
    public void operates(String companyKey, String brand) {
        operatedBrands.put(companyKey, brand);
    }

    public Map<String, String> operatedBrands() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(operatedBrands));
    }

    public void propose(AssistantProposal proposal) {
        this.proposal = proposal;
        onProposal.accept(proposal);
    }

    public List<AssistantStep> steps() {
        return List.copyOf(steps);
    }

    public AssistantProposal proposal() {
        return proposal;
    }
}
