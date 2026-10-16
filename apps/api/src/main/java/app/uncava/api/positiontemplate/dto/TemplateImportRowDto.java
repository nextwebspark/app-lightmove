package app.uncava.api.positiontemplate.dto;

import app.uncava.api.positiontemplate.constant.TemplateImportAction;
import app.uncava.api.positiontemplate.model.ImportedTemplate;
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
