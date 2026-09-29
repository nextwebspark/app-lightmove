package app.lightmove.api.enrichment.sourcing.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.TestLlmCallPolicy;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson;
import app.lightmove.api.enrichment.candidate.model.BrightDataPerson.BrightDataExperience;
import app.lightmove.api.enrichment.sourcing.model.SourcingBrief;
import app.lightmove.api.enrichment.sourcing.model.SourcingSpec;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExecutiveRerankerTest {

    private static final SourcingBrief BRIEF = new SourcingBrief("Group CFO", Seniority.C_SUITE, "Finance",
            "Dubai", "United Arab Emirates", List.of(), null, List.of());
    private static final SourcingSpec SPEC = SourcingSpec.of(List.of("Chief"), List.of("Finance"), List.of(),
            "The group's chief financial officer.");

    private static final List<BrightDataPerson> HITS = List.of(
            person("risalat-rehman", "Risalat Rehman", "Chief Financial Officer at DP World Jeddah", "SA"),
            person("ea-person", "An Assistant", "EA to the CFO", "AU"),
            person("group-director", "Group Director", "Group Director - Financial Accounts", "AE"));

    @Test
    @DisplayName("picks come back by index, best first, clamped and cut to what was asked for")
    void picksAreMappedAndOrdered() {
        RecordingChatModel model = new RecordingChatModel("""
                {"picks":[{"hit":3,"score":7,"reason":"Group finance seat in Dubai"},
                          {"hit":1,"score":14,"reason":" The Jeddah CFO "},
                          {"hit":9,"score":5,"reason":"nobody"},
                          {"hit":1,"score":2,"reason":"again"},
                          {"hit":2,"score":1,"reason":"an assistant"}],
                 "note":null}""");

        List<RerankedHit> picks = rerankerOver(model).pick(BRIEF, SPEC, "DP World", HITS, 2);

        assertThat(picks).containsExactly(new RerankedHit(0, 10, "The Jeddah CFO"),
                new RerankedHit(2, 7, "Group finance seat in Dubai"));
        assertThat(model.prompts.getLast()).contains("#1 · Risalat Rehman · Chief Financial Officer at DP World Jeddah",
                "#2 · An Assistant · EA to the CFO", "DP World", "Pick at most 2");
    }

    @Test
    @DisplayName("nobody fitting and a failed call are both no picks — the company reads as nothing fitting")
    void nobodyAndFailureAreNoPicks() {
        assertThat(rerankerOver(new RecordingChatModel("{\"picks\":[],\"note\":\"All juniors\"}"))
                .pick(BRIEF, SPEC, "DP World", HITS, 3)).isEmpty();
        assertThat(rerankerOver(new RecordingChatModel("not json at all"))
                .pick(BRIEF, SPEC, "DP World", HITS, 3)).isEmpty();
    }

    private static ExecutiveReranker rerankerOver(RecordingChatModel model) {
        return new ExecutiveReranker(TestLlmCallPolicy.promptsOver(model));
    }

    static BrightDataPerson person(String slug, String name, String position, String countryCode) {
        return new BrightDataPerson(slug, slug, name, "https://www.linkedin.com/in/" + slug, "About " + name,
                position, "Dubai", "Dubai, United Arab Emirates", countryCode, "DP World",
                new BrightDataPerson.BrightDataCurrentCompany("DP World", "dp-world", null), null, true,
                List.of(new BrightDataExperience("DP World", position, null, null, null, "2020", null, null, null)),
                List.of(), List.of(), List.of());
    }
}
