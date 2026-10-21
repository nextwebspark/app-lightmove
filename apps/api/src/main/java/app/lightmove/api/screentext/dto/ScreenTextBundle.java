package app.lightmove.api.screentext.dto;

import java.util.Map;

/** What every screen reads: the overrides now published, keyed by the SPA catalogue's keys. Defaults are the SPA's. */
public record ScreenTextBundle(String locale, int release, String version, Map<String, String> entries) {
}
