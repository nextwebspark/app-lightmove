package app.lightmove.api.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The bulk-add ceiling, enforced when the settings bind rather than when a user presses a button. */
class CompanyListSettingsTest {

    @Test
    @DisplayName("a limit past the ceiling is refused, naming the maximum")
    void refusesALimitPastTheCeiling() {
        // Binding throws, so an over-tuned COMPANY_BULK_ADD_LIMIT fails the deploy rather than every
        // bulk add. Past ~7,200 the insert breaks at the wire protocol; this stops well short of it.
        assertThatThrownBy(() ->
                new CompanyListSettings(25, 100, CompanyListSettings.MAX_BULK_ADD_LIMIT + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bulk-add-limit")
                .hasMessageContaining(String.valueOf(CompanyListSettings.MAX_BULK_ADD_LIMIT));
    }

    @Test
    @DisplayName("zero is refused too — a limit of nothing is a broken button, not a policy")
    void refusesANonPositiveLimit() {
        assertThatThrownBy(() -> new CompanyListSettings(25, 100, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the ceiling itself binds")
    void acceptsTheCeiling() {
        assertThat(new CompanyListSettings(25, 100, CompanyListSettings.MAX_BULK_ADD_LIMIT).bulkAddLimit())
                .isEqualTo(CompanyListSettings.MAX_BULK_ADD_LIMIT);
    }
}
