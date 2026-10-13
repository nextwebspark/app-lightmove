package app.lightmove.api.assistant.tool;

import app.lightmove.api.assistant.model.AssistantProposal;
import app.lightmove.api.assistant.model.AssistantQuestion;
import app.lightmove.api.assistant.model.AssistantStep;
import app.lightmove.api.assistant.model.AssistantStepEvent;
import app.lightmove.api.assistant.model.ModelSpend;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * What the tools did during one ask: the steps they reported, the companies they found or researched
 * by name, and the card they proposed. Each step
 * change is passed on as it happens, so the panel can show it live; the whole record is saved with
 * the answer. Every method is synchronized: the tools of one model round run side by side.
 */
@Slf4j
public class TurnRecorder {

    private final List<AssistantStep> steps = new ArrayList<>();
    private final Map<Integer, Long> openStepStartedAt = new HashMap<>();
    private final Consumer<AssistantStepEvent> onStep;
    private final Consumer<AssistantProposal> onProposal;
    private final Set<String> foundAccountIds = new LinkedHashSet<>();
    private final Map<String, CapturedCompanyDetails> researched = new LinkedHashMap<>();
    private final Map<String, CapturedCompanyDetails> remembered = new LinkedHashMap<>();
    private final Map<String, String> operatedBrands = new LinkedHashMap<>();
    private final Set<String> skillsUsed = new LinkedHashSet<>();
    private boolean namesLookedUp;
    private int vendorSearches;
    private AssistantProposal proposal;
    private ModelSpend spend = ModelSpend.NONE;
    private List<AssistantQuestion> questions = List.of();

    public TurnRecorder(Consumer<AssistantStepEvent> onStep) {
        this(onStep, proposal -> { });
    }

    /** {@code onProposal} hears the card the moment it is made, before the model writes its answer. */
    public TurnRecorder(Consumer<AssistantStepEvent> onStep, Consumer<AssistantProposal> onProposal) {
        this.onStep = onStep;
        this.onProposal = onProposal;
    }

    public synchronized int startStep(String label) {
        steps.add(new AssistantStep(label, null));
        int index = steps.size() - 1;
        openStepStartedAt.put(index, System.nanoTime());
        onStep.accept(new AssistantStepEvent(index, label, null, false));
        return index;
    }

    public synchronized void finishStep(int index, String detail) {
        String label = steps.get(index).label();
        steps.set(index, new AssistantStep(label, detail));
        Long startedAt = openStepStartedAt.remove(index);
        if (startedAt != null) {
            log.info("Assistant step '{}' took {}ms", label, (System.nanoTime() - startedAt) / 1_000_000);
        }
        onStep.accept(new AssistantStepEvent(index, label, detail, true));
    }

    /** True the first time only: an answer looks names up once, because every lookup is billed. */
    public synchronized boolean startNameLookup() {
        boolean first = !namesLookedUp;
        namesLookedUp = true;
        return first;
    }

    public synchronized void found(Collection<String> apolloAccountIds) {
        foundAccountIds.addAll(apolloAccountIds);
    }

    public synchronized List<String> foundAccountIds() {
        return List.copyOf(foundAccountIds);
    }

    public synchronized void countVendorSearches(int searches) {
        vendorSearches += searches;
    }

    /** Billed Bright Data searches this answer made — what its audit event records. */
    public synchronized int vendorSearches() {
        return vendorSearches;
    }

    public synchronized void usedSkill(String skill) {
        skillsUsed.add(skill);
    }

    /** Every playbook this answer loaded, in order, each once — what its audit event records. */
    public synchronized List<String> skillsUsed() {
        return List.copyOf(skillsUsed);
    }

    public synchronized void researched(String linkedinSlug, CapturedCompanyDetails details) {
        researched.put(linkedinSlug, details);
    }

    public synchronized Map<String, CapturedCompanyDetails> researched() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(researched));
    }

    /**
     * A page an earlier answer of this chat researched, so its company can be proposed again. Kept apart
     * from {@link #researched()}: a remembered company was not found by this answer and must not be
     * carded unasked.
     */
    public synchronized void remember(String linkedinSlug, CapturedCompanyDetails details) {
        remembered.put(linkedinSlug, details);
    }

    /** Every page this answer may card: its own research, then what earlier answers researched. */
    public synchronized Map<String, CapturedCompanyDetails> proposablePages() {
        Map<String, CapturedCompanyDetails> pages = new LinkedHashMap<>(remembered);
        pages.putAll(researched);
        return Collections.unmodifiableMap(pages);
    }

    /** Records that the company under {@code companyKey} runs {@code brand} locally, as a franchise partner does. */
    public synchronized void operates(String companyKey, String brand) {
        operatedBrands.put(companyKey, brand);
    }

    public synchronized Map<String, String> operatedBrands() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(operatedBrands));
    }

    public synchronized void propose(AssistantProposal proposal) {
        this.proposal = proposal;
        onProposal.accept(proposal);
    }

    public synchronized void spent(String model, Integer inputTokens, Integer outputTokens) {
        spend = spend.plus(model, inputTokens, outputTokens);
    }

    /** The tokens every model call of this answer took — what its turn row records. */
    public synchronized ModelSpend spend() {
        return spend;
    }

    /**
     * Keeps the questions for the card ({@link AssistantQuestions} has already held them to its limits). Only
     * the first set an answer asks is kept; an empty one leaves the answer to carry on without asking.
     */
    public synchronized void ask(List<AssistantQuestion> asked) {
        if (!questions.isEmpty() || asked.isEmpty()) {
            return;
        }
        questions = List.copyOf(asked);
        int step = startStep(questions.size() == 1 ? "Asking you a question" : "Asking you some questions");
        finishStep(step, null);
    }

    public synchronized List<AssistantQuestion> questions() {
        return questions;
    }

    public synchronized boolean askedQuestions() {
        return !questions.isEmpty();
    }

    public synchronized List<AssistantStep> steps() {
        return List.copyOf(steps);
    }

    public synchronized AssistantProposal proposal() {
        return proposal;
    }
}
