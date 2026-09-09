package app.lightmove.api.strategy.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A caller-authored range on one numeric axis — the "Custom Range" mode. Both ends are optional and
 * inclusive, and a range with neither end set is no constraint rather than an error, so a half-typed
 * pair narrows the search progressively. A non-null range <i>is</i> the custom mode; there is no flag
 * anywhere in the filter that could disagree with it.
 *
 * <p><b>Both Jackson annotations are load-bearing.</b> This record is nested inside the {@code filter}
 * jsonb column, and Jackson reads {@code isEmpty()} as a bean property: it wrote {@code "empty"} into
 * every stored document and then refused to read one back, so saving a Custom Range on either axis
 * left the mandate unreadable — the Strategy screen, its results, the report and bulk add all 500ing
 * on the next request. {@code @JsonIgnore} stops it being written; {@code ignoreUnknown} keeps the
 * documents already carrying it readable. Any derived accessor added here needs the same treatment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NumericRange(Long min, Long max) {

    @JsonIgnore
    public boolean isEmpty() {
        return min == null && max == null;
    }
}
