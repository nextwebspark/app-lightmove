package app.lightmove.api.screentext.dto;

import java.time.Instant;
import java.util.List;

/** One release as the Releases list shows it: what changed, by whom, and which release it restored if any. */
public record ScreenTextReleaseView(int number, String note, Integer restoredFrom, List<String> changedKeys,
                                    int overrideCount, String publishedByName, Instant publishedAt) {
}
