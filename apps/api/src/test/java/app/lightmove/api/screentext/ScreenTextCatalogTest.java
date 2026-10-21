package app.lightmove.api.screentext;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.screentext.service.ScreenTextCatalog;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ScreenTextCatalogTest {

    private final ScreenTextCatalog catalog = new ScreenTextCatalog(JsonMapper.builder().build());

    @Test
    @DisplayName("reads the SPA's catalogue, error wording included")
    void readsTheCatalogue() {
        assertThat(catalog.entry("errors-general.unexpected")).isPresent();
        assertThat(catalog.entry("errors-general.unexpected").orElseThrow().placeholders()).containsExactly("reference");
    }

    @Test
    @DisplayName("serves an override only for a key the code has, holding the placeholders its default holds")
    void servesOnlySafeOverrides() {
        Map<String, String> servable = catalog.servable(Map.of(
                "errors-general.unexpected", "Something broke. Quote {reference} to support.",
                "errors-general.generic", "Something went wrong. Try again in a moment.",
                "errors-general.gone", "A key the code no longer has.",
                "errors-account.LAST_ADMIN", catalog.entry("errors-account.LAST_ADMIN").orElseThrow().defaultText()));
        assertThat(servable).containsOnlyKeys("errors-general.unexpected", "errors-general.generic");

        assertThat(catalog.servable(Map.of("errors-general.unexpected", "Something broke."))).isEmpty();
    }
}
