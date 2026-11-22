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
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

/** The console's list of prompts against what ships, and every structured prompt against its Try it. */
class PlatformPromptTest {

    private static final Path SHIPPED_PROMPTS = Path.of("src/main/resources/prompts");

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
