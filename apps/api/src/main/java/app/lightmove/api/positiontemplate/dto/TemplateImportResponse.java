package app.lightmove.api.positiontemplate.dto;

import app.lightmove.api.positiontemplate.model.PlannedTemplateImport;
import java.util.List;

/** An import's plan (preview) or its outcome (commit), one row per template in the file, in file order. */
public record TemplateImportResponse(boolean committed, List<TemplateImportRowDto> rows) {

    public static TemplateImportResponse of(boolean committed, List<PlannedTemplateImport> plan) {
        return new TemplateImportResponse(committed, plan.stream()
                .map(step -> TemplateImportRowDto.of(step.template(), step.action()))
                .toList());
    }
}
