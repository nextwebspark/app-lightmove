package app.lightmove.api.strategy.dto;

import app.lightmove.api.strategy.model.PeopleLanguage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PeopleLanguageDto(
        @NotBlank(message = "A language is required")
        @Size(max = 64)
        String language,

        @Size(max = 5, message = "Too many proficiencies selected")
        List<@Size(max = 32) String> proficiencies
) {

    public static PeopleLanguageDto of(PeopleLanguage language) {
        return new PeopleLanguageDto(language.language(), language.proficiencies());
    }
}
