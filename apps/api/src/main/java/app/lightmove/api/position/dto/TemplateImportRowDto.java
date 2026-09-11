package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.TemplateImportAction;
import app.lightmove.api.position.model.ImportedTemplate;
import java.util.List;

public record TemplateImportRowDto(String code, String title, TemplateImportAction action,
                                   List<TemplateImportProblemDto> problems) {

    public static TemplateImportRowDto of(ImportedTemplate template, TemplateImportAction action) {
        return new TemplateImportRowDto(template.code(), template.title(), action,
                template.problems().stream()
                        .map(problem -> new TemplateImportProblemDto(problem.field(), problem.message()))
                        .toList());
    }
}
