package app.lightmove.api.promptregistry;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.llm.constant.PromptKind;
import app.lightmove.api.core.llm.service.PromptTrial;
import app.lightmove.api.promptregistry.constant.PlatformPrompt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/** The console's list of prompts against what ships, and every structured prompt against its Try it. */
class PlatformPromptTest {

    private static final Path SHIPPED_PROMPTS = Path.of("src/main/resources/prompts");
    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final String OUTCOMES_IMPORT = "import app.lightmove.api.core.llm.service.PromptOutcomes;";

    // Every class that reports a person's decision, and the prompts whose answers it reports. CandidateService
    // reports the background values Person.inferredBy() names, which enrichment and nationality propose.
    private static final Map<String, Set<String>> DECISIONS_REPORTED_BY = Map.of(
            "app/lightmove/api/outreach/service/OutreachEnrollmentService.java", Set.of("outreach-opener"),
            "app/lightmove/api/candidate/service/CandidateService.java",
            Set.of("candidate-ai-enrich", "candidate-nationality"),
            "app/lightmove/api/position/service/PositionSuggestions.java",
            Set.of("position-extract-details", "position-extract-context", "position-extract-reporting",
                    "position-extract-assessment"));

    @Test
    @DisplayName("the console lists exactly the prompts that ship")
    void listsExactlyTheShippedPrompts() throws IOException {
        try (Stream<Path> files = Files.list(SHIPPED_PROMPTS)) {
            List<String> shipped = files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".st"))
                    .map(name -> name.endsWith("-system.st")
                            ? name.substring(0, name.length() - "-system.st".length())
                            : name.substring(0, name.length() - ".st".length()))
                    .toList();

            assertThat(Arrays.stream(PlatformPrompt.values()).map(PlatformPrompt::getPromptId))
                    .containsExactlyInAnyOrderElementsOf(shipped);
        }
    }

    @Test
    @DisplayName("every prompt answered as a document has a Try it, and no Try it names a prompt that is not listed")
    void everyStructuredPromptCanBeTried() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(PromptTrial.class));
        List<String> tried = scanner.findCandidateComponents("app.lightmove.api").stream()
                .map(BeanDefinition::getBeanClassName)
                .map(PlatformPromptTest::promptIdDeclaredBy)
                .toList();

        assertThat(tried).doesNotHaveDuplicates();
        assertThat(tried).containsExactlyInAnyOrderElementsOf(Arrays.stream(PlatformPrompt.values())
                .filter(prompt -> prompt.getKind() == PromptKind.STRUCTURED)
                .map(PlatformPrompt::getPromptId)
                .toList());
    }

    @Test
    @DisplayName("a prompt has a kept share exactly when a feature reports what people did with its answers")
    void decisionsCountedMatchesWhereDecisionsAreReported() throws IOException {
        try (Stream<Path> sources = Files.walk(MAIN_SOURCES)) {
            List<String> reporters = sources.filter(source -> source.toString().endsWith(".java"))
                    .filter(PlatformPromptTest::importsPromptOutcomes)
                    .map(source -> MAIN_SOURCES.relativize(source).toString().replace('\\', '/'))
                    .toList();
            assertThat(reporters)
                    .as("a new caller of PromptOutcomes: add it here and set decisionsCounted on its prompts")
                    .containsExactlyInAnyOrderElementsOf(DECISIONS_REPORTED_BY.keySet());
        }

        assertThat(Arrays.stream(PlatformPrompt.values())
                .filter(PlatformPrompt::isDecisionsCounted)
                .map(PlatformPrompt::getPromptId))
                .containsExactlyInAnyOrderElementsOf(DECISIONS_REPORTED_BY.values().stream()
                        .flatMap(Set::stream)
                        .toList());
    }

    private static boolean importsPromptOutcomes(Path source) {
        try {
            return Files.readString(source).contains(OUTCOMES_IMPORT);
        } catch (IOException unreadable) {
            throw new IllegalStateException("Could not read " + source, unreadable);
        }
    }

    /** Read off the source, since a trial's collaborators cannot be built here. */
    private static String promptIdDeclaredBy(String className) {
        Path source = Path.of("src/main/java", className.replace('.', '/') + ".java");
        try {
            String text = Files.readString(source);
            int method = text.indexOf("String promptId()");
            int open = text.indexOf('"', method);
            return text.substring(open + 1, text.indexOf('"', open + 1));
        } catch (IOException unreadable) {
            throw new IllegalStateException("Could not read " + source, unreadable);
        }
    }
}
