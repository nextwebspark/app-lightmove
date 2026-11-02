package app.lightmove.api.core.platformconfig;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.platformconfig.constant.PlatformPrompt;
import app.lightmove.api.core.platformconfig.service.ShippedPrompts;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class PlatformPromptCatalogTest {

    @Test
    @DisplayName("every shipped system prompt is listed, and nothing the jar does not ship")
    void catalogMatchesThePromptsDirectory() throws IOException {
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:prompts/*.st");
        var shipped = Arrays.stream(files)
                .map(Resource::getFilename)
                .filter(name -> !name.contains("-baseline-"))
                .map(name -> "prompts/" + name)
                .collect(Collectors.toSet());

        assertThat(Arrays.stream(PlatformPrompt.values()).map(PlatformPrompt::resource))
                .containsExactlyInAnyOrderElementsOf(shipped);
    }

    @Test
    @DisplayName("the assistant's prompt names its two variables, and every other prompt names none")
    void placeholdersAreReadFromTheShippedText() {
        ShippedPrompts shipped = new ShippedPrompts();

        assertThat(ShippedPrompts.placeholdersIn(shipped.of(PlatformPrompt.ASSISTANT_CORE)))
                .containsExactly("brief", "hiring");
        assertThat(Arrays.stream(PlatformPrompt.values()).filter(prompt -> prompt != PlatformPrompt.ASSISTANT_CORE))
                .allSatisfy(prompt -> assertThat(ShippedPrompts.placeholdersIn(shipped.of(prompt))).isEmpty());
    }

    @Test
    @DisplayName("an id the catalog does not list still reads its file, as an eval's frozen baseline does")
    void uncataloguedIdsFallBackToTheirFile() {
        assertThat(new ShippedPrompts().systemTextOf("position-extract-assessment-baseline")).isNotBlank();
    }
}
