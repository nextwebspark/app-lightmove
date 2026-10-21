package app.lightmove.api.screentext.model;

import java.util.List;

/** {@code screen-text/catalog.json} as the SPA's suite writes it. */
public record ScreenTextCatalogFile(String format, int formatVersion, List<String> neverOnScreen,
                                    List<ScreenTextCatalogScreen> screens, List<ScreenTextCatalogEntry> entries) {
}
