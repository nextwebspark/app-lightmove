package app.lightmove.api.screentext.dto;

import java.time.Instant;
import java.util.List;

/**
 * One editable sentence: its default, what is published, and the draft waiting if any. {@code publishedStale} marks
 * an override the screens no longer draw because the default it replaced has since changed its placeholders.
 */
public record ScreenTextEntryView(String key, String screen, String kind, String where, String defaultText,
                                  List<String> placeholders, int maxLength, String publishedText,
                                  boolean publishedStale, String draftText, Long draftVersion,
                                  String draftUpdatedByName, Instant draftUpdatedAt) {
}
