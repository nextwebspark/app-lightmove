package app.lightmove.api.enrichment.sourcing.service;

import static app.lightmove.api.enrichment.sourcing.service.PromptText.NOT_STATED;
import static app.lightmove.api.enrichment.sourcing.service.PromptText.orNotStated;

import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Rewrites a company's title words when a search there found nobody, reading every word an earlier
 * round tried as one {@link RefineConversation}. Empty on any failure, on a blocked answer and when the
 * model gives up — the company's search simply stops there.
 */
@Service
@Slf4j
public class SourcingSpecRefiner {

    private static final String PROMPT_ID = "executive-sourcing-refine";

    private static final String BLOCKED = "{\"seniorityWords\":[],\"functionWords\":[],\"excludedWords\":[],"
            + "\"why\":\"" + BlockedAnswer.MARKER + "\"}";

    private final StructuredPrompt prompt;
    private final ObjectMapper json;

    public SourcingSpecRefiner(StructuredPromptFactory prompts, ObjectMapper json) {
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
        this.json = json;
    }

    RefineConversation open(SourcingBrief brief, String companyName) {
        RefineConversation conversation = new RefineConversation();
        conversation.tell("""
                THE ROLE
                Title: %s
                Seniority: %s
                Department: %s
                Location: %s

                THE COMPANY: %s""".formatted(orNotStated(brief.roleTitle()),
                brief.seniority() == null ? NOT_STATED : brief.seniority().name(),
                orNotStated(brief.department()), orNotStated(brief.locationLine()), companyName));
        return conversation;
    }

    void reportNobodyFound(RefineConversation conversation, int round, SourcingSpec searched) {
        conversation.tell("ROUND " + round + " SEARCHED — found nobody\n" + wordsOf(searched));
    }

    void reportRepeat(RefineConversation conversation, int repeatedRound) {
        conversation.tell("That answer repeats the words round " + repeatedRound + " already searched. Answer a "
                + "combination no round has tried, or three empty lists if you have no better idea.");
    }

    Optional<SourcingSpec> refine(RefineConversation conversation, int nextRound, String roleSummary) {
        conversation.tell("Propose the words for round " + nextRound + ".");
        try {
            ModelAnswer answered = prompt.ask(ModelAnswer.class, conversation.history(),
                    user -> user.text("{turn}").param("turn", conversation.nextTurn()));
            if (answered == null || BlockedAnswer.matches(answered.why())) {
                return Optional.empty();
            }
            conversation.answered(json.writeValueAsString(replayOf(answered)));
            SourcingSpec spec = SourcingSpec.of(answered.seniorityWords(), answered.functionWords(),
                    answered.excludedWords(), roleSummary);
            log.info("Sourcing round {} reworded to {}: {}", nextRound, spec.wordKey(), answered.why());
            return spec.seniorityWords().isEmpty() || spec.functionWords().isEmpty() ? Optional.empty()
                    : Optional.of(spec);
        } catch (RuntimeException e) {
            log.warn("Sourcing refinement skipped: {}", e.toString());
            return Optional.empty();
        }
    }

    private static Map<String, Object> replayOf(ModelAnswer answered) {
        Map<String, Object> replay = new LinkedHashMap<>();
        replay.put("seniorityWords", answered.seniorityWords() == null ? List.of() : answered.seniorityWords());
        replay.put("functionWords", answered.functionWords() == null ? List.of() : answered.functionWords());
        replay.put("excludedWords", answered.excludedWords() == null ? List.of() : answered.excludedWords());
        replay.put("why", answered.why());
        return replay;
    }

    private static String wordsOf(SourcingSpec spec) {
        return "Seniority: " + listed(spec.seniorityWords()) + "\nFunction: " + listed(spec.functionWords())
                + "\nExcluded: " + listed(spec.excludedWords());
    }

    private static String listed(List<String> words) {
        return words.isEmpty() ? "none" : String.join(", ", words);
    }

    private record ModelAnswer(List<String> seniorityWords, List<String> functionWords, List<String> excludedWords,
                               String why) {}
}
