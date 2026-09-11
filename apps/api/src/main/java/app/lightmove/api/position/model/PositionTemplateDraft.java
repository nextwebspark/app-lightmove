package app.lightmove.api.position.model;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.position.constant.PositionDiscipline;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Everything an editor may change about a template — all of it but the owner and the code. The one
 * shape the editor, the import file and the entity exchange, so "did anything change?" is a record
 * comparison once both sides are {@link #normalised()}.
 */
public record PositionTemplateDraft(
        String title,
        PositionDiscipline discipline,
        Seniority seniority,
        String summary,
        List<String> keywords,
        PositionTemplateBody body
) {

    public PositionTemplateDraft {
        keywords = keywords == null ? List.of() : keywords.stream().filter(Objects::nonNull).toList();
        body = body == null ? PositionTemplateBody.empty() : body;
    }

    /** Keywords are lower-cased because {@link PositionTemplate#matchesTitle} compares them that way. */
    public PositionTemplateDraft normalised() {
        return new PositionTemplateDraft(
                title == null ? null : title.trim(),
                discipline,
                seniority,
                summary == null || summary.isBlank() ? null : summary.trim(),
                keywords.stream()
                        .map(keyword -> keyword.trim().toLowerCase(Locale.ROOT))
                        .filter(keyword -> !keyword.isEmpty())
                        .distinct()
                        .toList(),
                body.normalised());
    }
}
