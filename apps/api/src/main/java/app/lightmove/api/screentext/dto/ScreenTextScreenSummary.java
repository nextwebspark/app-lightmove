package app.lightmove.api.screentext.dto;

/** One screen group in the editor's rail: how many of its sentences differ from the default, and how many wait to publish. */
public record ScreenTextScreenSummary(String id, String title, int entryCount, int changedCount, int draftCount) {
}
