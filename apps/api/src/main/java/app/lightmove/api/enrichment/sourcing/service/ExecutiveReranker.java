package app.lightmove.api.enrichment.sourcing.service;

import static app.lightmove.api.enrichment.sourcing.service.PromptText.NOT_STATED;
import static app.lightmove.api.enrichment.sourcing.service.PromptText.orNotStated;

import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.EnrichedProfile;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.service.BrightDataPersonProfiles;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * One model call per company: the hits, thinned to what says whether someone fits — name, title,
 * location, a line of their About and their last three posts — and back come at most {@code picks}
 * of them with a score and a reason. Empty on any failure; the company is then reported as nothing
 * fitting rather than the run failing.
 */
@Service
@Slf4j
public class ExecutiveReranker {

    private static final String PROMPT_ID = "executive-sourcing-rerank";
    private static final int MAX_ABOUT = 200;
    private static final int MAX_CAREER_LINES = 3;
    private static final int MIN_SCORE = 1;
    private static final int MAX_SCORE = 10;

    private static final String BLOCKED = "{\"picks\":[],\"note\":\"" + BlockedAnswer.MARKER + "\"}";

    private final StructuredPrompt prompt;

    public ExecutiveReranker(StructuredPromptFactory prompts) {
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
    }

    /** Picks over {@code hits}, by index into it, best first; empty when nobody fits or the call failed. */
    public List<RerankedHit> pick(SourcingBrief brief, SourcingSpec spec, String companyName,
                                            List<BrightDataPerson> hits, int picks) {
        try {
            ModelAnswer answered = prompt.ask(ModelAnswer.class, user -> user.text("""
                    THE ROLE
                    Title: {roleTitle}
                    Seniority: {seniority}
                    Summary: {summary}
                    Location: {location}

                    THE COMPANY: {company}

                    THE PEOPLE (LinkedIn says each works there today)
                    {people}

                    Pick at most {picks}.
                    """)
                    .param("roleTitle", orNotStated(brief.roleTitle()))
                    .param("seniority", brief.seniority() == null ? NOT_STATED : brief.seniority().name())
                    .param("summary", orNotStated(spec.roleSummary()))
                    .param("location", orNotStated(brief.locationLine()))
                    .param("company", companyName)
                    .param("people", peopleOf(hits))
                    .param("picks", picks));
            if (answered == null || BlockedAnswer.matches(answered.note())) {
                return List.of();
            }
            return picksOf(answered, hits.size(), picks);
        } catch (RuntimeException e) {
            log.warn("Executive rerank at {} skipped: {}", companyName, e.toString());
            return List.of();
        }
    }

    /** Unknown or repeated numbers are dropped, scores clamped, and the list cut to what was asked for. */
    private static List<RerankedHit> picksOf(ModelAnswer answered, int shown, int picks) {
        if (answered.picks() == null) {
            return List.of();
        }
        Set<Integer> seen = new HashSet<>();
        List<RerankedHit> kept = new ArrayList<>();
        for (ModelPick pick : answered.picks()) {
            if (pick == null || pick.hit() == null || pick.hit() < 1 || pick.hit() > shown
                    || !seen.add(pick.hit())) {
                continue;
            }
            int score = pick.score() == null ? MIN_SCORE : Math.max(MIN_SCORE, Math.min(MAX_SCORE, pick.score()));
            kept.add(new RerankedHit(pick.hit() - 1, score,
                    pick.reason() == null || pick.reason().isBlank() ? null : pick.reason().strip()));
        }
        kept.sort((left, right) -> Integer.compare(right.score(), left.score()));
        return kept.stream().limit(picks).toList();
    }

    static String peopleOf(List<BrightDataPerson> hits) {
        StringBuilder people = new StringBuilder();
        for (int index = 0; index < hits.size(); index++) {
            BrightDataPerson person = hits.get(index);
            EnrichedProfile profile = BrightDataPersonProfiles.toEnrichedProfile(person);
            people.append('#').append(index + 1).append(" · ").append(orNotStated(person.name()))
                    .append(" · ").append(orNotStated(person.position()))
                    .append(" · ").append(Stream.of(profile.locationCity(), profile.locationCountry())
                            .filter(Objects::nonNull).collect(Collectors.joining(", ")))
                    .append('\n');
            if (profile.about() != null) {
                people.append("   About: ").append(shortened(profile.about())).append('\n');
            }
            for (CandidateCareerEntry post : profile.career().stream().limit(MAX_CAREER_LINES).toList()) {
                people.append("   - ").append(orNotStated(post.title())).append(" at ")
                        .append(orNotStated(post.company()))
                        .append(post.period() == null ? "" : " (" + post.period() + ")").append('\n');
            }
        }
        return people.isEmpty() ? "nobody" : people.toString();
    }

    private static String shortened(String text) {
        String flattened = text.replaceAll("\\s+", " ").strip();
        return flattened.length() <= MAX_ABOUT ? flattened : flattened.substring(0, MAX_ABOUT) + "…";
    }

    private record ModelAnswer(List<ModelPick> picks, String note) {}

    private record ModelPick(Integer hit, Integer score, String reason) {}
}
