package app.lightmove.api.screentext.model;

import java.util.List;

/** One editable sentence as the SPA's catalogue describes it: where it shows, its default and what it must keep. */
public record ScreenTextCatalogEntry(String key, String screen, String kind, String where, String defaultText,
                                     List<String> placeholders, int maxLength) {

    public ScreenTextCatalogEntry {
        placeholders = placeholders == null ? List.of() : List.copyOf(placeholders);
    }
}
