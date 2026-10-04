package app.lightmove.api.enrichment.sourcing.service;

import static app.lightmove.api.enrichment.sourcing.service.PromptText.NOT_STATED;
import static app.lightmove.api.enrichment.sourcing.service.PromptText.orNotStated;

import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * One model call per run: the brief becomes the single words a fitting title carries. Every failure
 * answers empty and the caller falls back to {@link SourcingSpec#defaultFor}, so a blocked or
 * unreachable model never stops a run.
 */
@Service
@Slf4j
public class SourcingSpecProposer {

    private static final String PROMPT_ID = "executive-sourcing-spec";

    private static final String BLOCKED = "{\"seniorityWords\":[],\"functionWords\":[],\"excludedWords\":[],"
            + "\"roleSummary\":\"" + BlockedAnswer.MARKER + "\"}";

    private final StructuredPrompt prompt;

    public SourcingSpecProposer(StructuredPromptFactory prompts) {
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
    }

    /**
     * Empty unless both title lists hold a word: either list alone searches on the other, and "Chief"
     * alone bought every Chief Accountant at the company.
     */
    public Optional<SourcingSpec> propose(SourcingBrief brief) {
        try {
            ModelAnswer answered = prompt.ask(ModelAnswer.class, user -> user.text("""
                    THE ROLE
                    Title: {roleTitle}
                    Seniority: {seniority}
                    Department: {department}
                    Location: {location}

                    Responsibilities:
                    {responsibilities}

                    Narrative: {narrative}

                    Technical competencies: {competencies}
                    """)
                    .param("roleTitle", orNotStated(brief.roleTitle()))
                    .param("seniority", brief.seniority() == null ? NOT_STATED : brief.seniority().name())
                    .param("department", orNotStated(brief.department()))
                    .param("location", orNotStated(brief.locationLine()))
                    .param("responsibilities", brief.responsibilities().isEmpty() ? NOT_STATED
                            : brief.responsibilities().stream().map(line -> "- " + line)
                                    .collect(Collectors.joining("\n")))
                    .param("narrative", orNotStated(brief.narrative()))
                    .param("competencies", brief.technicalCompetencies().isEmpty() ? NOT_STATED
                            : String.join(", ", brief.technicalCompetencies())));
            if (answered == null || BlockedAnswer.matches(answered.roleSummary())) {
                return Optional.empty();
            }
            SourcingSpec spec = SourcingSpec.of(answered.seniorityWords(), answered.functionWords(),
                    answered.excludedWords(), answered.roleSummary());
            return spec.seniorityWords().isEmpty() || spec.functionWords().isEmpty() ? Optional.empty()
                    : Optional.of(spec);
        } catch (RuntimeException e) {
            log.warn("Sourcing spec proposal skipped: {}", e.toString());
            return Optional.empty();
        }
    }

    private record ModelAnswer(List<String> seniorityWords, List<String> functionWords, List<String> excludedWords,
                               String roleSummary) {}
}
