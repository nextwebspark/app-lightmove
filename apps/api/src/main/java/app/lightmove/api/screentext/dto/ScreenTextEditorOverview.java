package app.lightmove.api.screentext.dto;

import java.util.List;

/** The editor's frame: its screens, the release on air, and what the next publish would carry. */
public record ScreenTextEditorOverview(String locale, int latestRelease, int unpublishedCount,
                                       List<ScreenTextScreenSummary> screens) {
}
