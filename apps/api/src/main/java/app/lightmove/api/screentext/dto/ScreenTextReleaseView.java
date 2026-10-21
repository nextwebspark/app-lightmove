package app.lightmove.api.screentext.dto;

import java.time.Instant;
import java.util.List;

public record ScreenTextReleaseView(int number, String note, Integer restoredFrom, List<String> changedKeys,
                                    int overrideCount, String publishedByName, Instant publishedAt) {
}
