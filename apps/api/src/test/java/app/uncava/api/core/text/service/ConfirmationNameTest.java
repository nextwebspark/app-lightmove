package app.uncava.api.core.text.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfirmationNameTest {

    @Test
    @DisplayName("case, surrounding space and runs of spaces do not matter")
    void forgivesCaseAndSpacing() {
        assertThat(ConfirmationName.matches("Chief Financial Officer", "  chief   financial officer ")).isTrue();
    }

    @Test
    @DisplayName("a hyphen typed for an en or em dash matches")
    void forgivesDashes() {
        assertThat(ConfirmationName.matches("Group CFO – Energy Division", "group cfo - energy division")).isTrue();
        assertThat(ConfirmationName.matches("Head of Risk — KSA", "Head of Risk - KSA")).isTrue();
    }

    @Test
    @DisplayName("a different name, a prefix or nothing at all does not match")
    void refusesAnythingElse() {
        assertThat(ConfirmationName.matches("Chief Financial Officer", "Chief Financial")).isFalse();
        assertThat(ConfirmationName.matches("Chief Financial Officer", "Chief Operating Officer")).isFalse();
        assertThat(ConfirmationName.matches("Chief Financial Officer", "   ")).isFalse();
        assertThat(ConfirmationName.matches("Chief Financial Officer", null)).isFalse();
    }
}
