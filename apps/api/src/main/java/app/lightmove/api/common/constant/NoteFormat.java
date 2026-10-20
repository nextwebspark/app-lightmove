package app.lightmove.api.common.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * How a note's words were stored: as typed, or as the editor's Markdown. A note filed by a door (a form, the plugin,
 * a sheet) is plain, so a stray "*" or "1." in it is never read as formatting.
 */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum NoteFormat implements ApiValueEnum {

    PLAIN("plain"),
    MARKDOWN("markdown");

    private final String value;
}
