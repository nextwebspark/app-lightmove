package app.lightmove.api.enrichment.sourcing.service;

import static app.lightmove.api.enrichment.sourcing.service.PromptText.NOT_STATED;
import static app.lightmove.api.enrichment.sourcing.service.PromptText.orNotStated;

import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.enrichment.sourcing.constant.SearchLevel;
import app.lightmove.api.enrichment.sourcing.model.SearchWords;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * One model call per search: the brief becomes the words a fitting title holds — the whole function,
 * a range of levels below the seat, the titles that stand alone — in the languages of the countries the
 * companies are in. Every failure answers empty and the caller falls back to {@link SearchWords#defaultFor},
 * so a blocked or unreachable model never stops a search.
 */
@Service
@Slf4j
public class SearchWordsDrafter {

    private static final String PROMPT_ID = "executive-sourcing-words";

    private static final String BLOCKED = "{\"levels\":[],\"functionTerms\":[],\"localLevelWords\":[],"
            + "\"standaloneTitles\":[],\"excludedWords\":[],\"suggestions\":[],\"roleSummary\":\""
            + BlockedAnswer.MARKER + "\"}";

    private final StructuredPrompt prompt;

    public SearchWordsDrafter(StructuredPromptFactory prompts) {
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
    }

    /** {@code companyCountries} are the countries the companies searched are in, by name; empty when unknown. */
    public Optional<SearchWords> draft(SourcingBrief brief, List<String> companyCountries) {
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

                    Companies being searched are in: {companyCountries}
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
                            : String.join(", ", brief.technicalCompetencies()))
                    .param("companyCountries", companyCountries.isEmpty() ? NOT_STATED
                            : String.join(", ", companyCountries)));
            if (answered == null || BlockedAnswer.matches(answered.roleSummary())) {
                return Optional.empty();
            }
            SearchWords words = new SearchWords(levelsOf(answered.levels()), answered.functionTerms(),
                    answered.standaloneTitles(), answered.localLevelWords(), answered.excludedWords(),
                    answered.suggestions(), answered.roleSummary());
            return words.isSearchable() ? Optional.of(words) : Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Search words were not drafted: {}", e.toString());
            return Optional.empty();
        }
    }

    /** Bands the model named that exist; an unknown one is left out rather than failing the draft. */
    private static List<SearchLevel> levelsOf(List<String> named) {
        if (named == null) {
            return List.of();
        }
        return named.stream()
                .filter(name -> name != null && Arrays.stream(SearchLevel.values())
                        .anyMatch(level -> level.name().equals(name.strip().toUpperCase(Locale.ROOT))))
                .map(name -> SearchLevel.valueOf(name.strip().toUpperCase(Locale.ROOT)))
                .toList();
    }

    private record ModelAnswer(List<String> levels, List<String> functionTerms, List<String> localLevelWords,
                               List<String> standaloneTitles, List<String> excludedWords, List<String> suggestions,
                               String roleSummary) {}
}
