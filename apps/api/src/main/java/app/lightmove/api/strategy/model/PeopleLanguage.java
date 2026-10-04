package app.lightmove.api.strategy.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** One language a person search requires, at any of the proficiencies given, or at any when none are. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PeopleLanguage(String language, List<String> proficiencies) {

    public PeopleLanguage {
        proficiencies = proficiencies == null ? List.of() : List.copyOf(proficiencies);
    }
}
