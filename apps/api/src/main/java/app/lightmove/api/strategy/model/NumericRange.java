package app.lightmove.api.strategy.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A Custom Range: both ends optional and inclusive; neither set is no constraint. Both Jackson
 * annotations are load-bearing — Jackson wrote {@code isEmpty()} into the stored filter as
 * {@code "empty"} and then refused to read it back, 500ing the Strategy screen. Any derived accessor
 * needs the same treatment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NumericRange(Long min, Long max) {

    @JsonIgnore
    public boolean isEmpty() {
        return min == null && max == null;
    }
}
