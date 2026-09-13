package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.TemplateImportAction;
import java.util.List;

/** One template from an import file and what the import does with it. {@code target} is the row it changes. */
public record PlannedTemplateImport(ImportedTemplate template, TemplateImportAction action,
                                    PositionTemplate target) {

    public static long count(List<PlannedTemplateImport> plan, TemplateImportAction action) {
        return plan.stream().filter(step -> step.action() == action).count();
    }
}
