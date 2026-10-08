package app.lightmove.api.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssistantSkillsTest {

    private final AssistantSkills shipped = new AssistantSkills();

    @Test
    @DisplayName("the shipped playbooks are offered by name and description, never by where they sit on disk")
    void offersTheShippedPlaybooks() {
        assertThat(shipped.names())
                .containsExactlyInAnyOrder("find-companies", "recommend-sectors", "earlier-list", "mapped-executives");
        assertThat(shipped.tool().getToolDefinition().name()).isEqualTo(AssistantSkills.TOOL_NAME);
        assertThat(shipped.tool().getToolDefinition().description())
                .contains("<name>earlier-list</name>")
                .contains("Load it before any other tool");

        String loaded = shipped.tool().call("{\"command\":\"find-companies\"}");

        assertThat(loaded).contains("searchCompanyUniverse").doesNotContain("Base directory");
    }

    @Test
    @DisplayName("asked what an earlier list held, the playbook lets the answer name them")
    void letsAnEarlierListBeListed() {
        String loaded = shipped.tool().call("{\"command\":\"earlier-list\"}");

        assertThat(loaded).contains("list them from the block").contains("Call no tool for this");
    }

    @Test
    @DisplayName("no playbook calls the suggested companies a card")
    void neverCallsTheListACard() {
        for (String name : shipped.names()) {
            assertThat(shipped.tool().call("{\"command\":\"" + name + "\"}")).as(name).doesNotContainIgnoringCase("card");
        }
    }

    @Test
    @DisplayName("a playbook with no usable name, no description, no instructions or a taken name stops the start")
    void refusesAMalformedPlaybook() {
        assertThatThrownBy(() -> new AssistantSkills(List.of(playbook("Find Companies", "Finds companies", "Do it"))))
                .hasMessageContaining("hyphenated name");
        assertThatThrownBy(() -> new AssistantSkills(List.of(playbook("find", "", "Do it"))))
                .hasMessageContaining("description");
        assertThatThrownBy(() -> new AssistantSkills(List.of(playbook("find", "x".repeat(1025), "Do it"))))
                .hasMessageContaining("description");
        assertThatThrownBy(() -> new AssistantSkills(List.of(playbook("find", "Finds companies", ""))))
                .hasMessageContaining("no instructions");
        assertThatThrownBy(() -> new AssistantSkills(List.of(
                playbook("find", "Finds companies", "Do it"), playbook("find", "Finds more", "Do it again"))))
                .hasMessageContaining("Two assistant skills");
        assertThatThrownBy(() -> new AssistantSkills(List.of()))
                .hasMessageContaining("No assistant skills");
    }

    private static String playbook(String name, String description, String body) {
        return "---\nname: " + name + "\ndescription: " + description + "\n---\n\n" + body;
    }
}
