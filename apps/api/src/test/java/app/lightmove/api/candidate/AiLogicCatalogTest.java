package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.AiUse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #919: every prompt the app runs either leaves something on a person — and is an {@link AiUse} whose fixed text a
 * subject access export gives (Art. 15(1)(h)) — or is listed here, reviewed, as leaving nothing on anyone. A new
 * prompt fails until someone decides which it is.
 */
class AiLogicCatalogTest {

    /** Prompts that store nothing about a person, each with why. */
    private static final Map<String, String> LEAVE_NOTHING_ON_A_PERSON = Map.ofEntries(
            Map.entry("assistant-core.st", "the research assistant's chats are the consultant's, kept per user"
                    + " (app_lm_assistant_turn), never on a person's record"),
            Map.entry("recruiter-shortlist-system.st", "the /llm/shortlist smoke test: stateless, reads no workspace"
                    + " data and stores nothing"),
            Map.entry("executive-sourcing-words-system.st", "turns a brief into title words; no person in or out"),
            Map.entry("import-column-mapping-system.st", "maps spreadsheet headers; never sees a cell value"),
            Map.entry("position-extract-assessment-system.st", "reads a position description; no person"),
            Map.entry("position-extract-context-system.st", "reads a position description; no person"),
            Map.entry("position-extract-details-system.st", "reads a position description; no person"),
            Map.entry("position-extract-reporting-system.st", "reads a position description; no person"));

    @Test
    @DisplayName("every prompt is an explained AI use or reviewed as leaving nothing on a person")
    void everyPromptIsDecided() throws IOException {
        Set<String> prompts;
        try (Stream<Path> files = Files.list(Path.of("src", "main", "resources", "prompts"))) {
            prompts = files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(".st"))
                    .collect(Collectors.toSet());
        }
        Set<String> explained = Arrays.stream(AiUse.values()).map(AiUse::promptFile).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> decided = new HashSet<>(explained);
        decided.addAll(LEAVE_NOTHING_ON_A_PERSON.keySet());

        assertThat(decided).as("prompts neither explained by an AiUse nor reviewed here").containsAll(prompts);
        assertThat(prompts).as("prompt files named here that do not exist").containsAll(decided);
        assertThat(explained).as("a prompt both explained and said to leave nothing")
                .doesNotContainAnyElementsOf(LEAVE_NOTHING_ON_A_PERSON.keySet());
    }

    @Test
    @DisplayName("each AI use carries words of its own")
    void everyUseIsWorded() {
        for (AiUse use : AiUse.values()) {
            assertThat(use.title()).as(use.name()).isNotBlank();
            assertThat(use.logic()).as(use.name()).hasSizeGreaterThan(120);
        }
        assertThat(Arrays.stream(AiUse.values()).map(AiUse::logic).distinct()).hasSize(AiUse.values().length);
    }
}
