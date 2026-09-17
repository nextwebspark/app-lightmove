package app.lightmove.api.positiontemplate.dto;

import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.positiontemplate.constant.PositionDiscipline;
import app.lightmove.api.positiontemplate.model.PositionTemplateBody;
import app.lightmove.api.positiontemplate.model.PositionTemplateDraft;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A template save, in either scope. The field-by-field rules on the body live in
 * {@code PositionTemplateValidator}, which the import path runs too.
 */
public record PositionTemplateWriteRequest(
        @NotBlank(message = "Give the template a title")
        @Size(max = 160, message = "That title is too long")
        String title,

        @NotNull(message = "Choose a discipline")
        PositionDiscipline discipline,

        @NotNull(message = "Choose a seniority")
        Seniority seniority,

        @Size(max = 300, message = "That summary is too long")
        String summary,

        List<String> keywords,

        @NotNull(message = "The template has no content")
        PositionTemplateBody body,

        /** The version the editor opened. Ignored when creating. */
        Long version
) {

    public PositionTemplateDraft draft() {
        return new PositionTemplateDraft(title, discipline, seniority, summary, keywords, body);
    }
}
