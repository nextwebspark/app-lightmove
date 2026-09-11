package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.TemplateImportAction;

/** One template from an import file and what the import does with it. {@code target} is the row it changes. */
public record PlannedTemplateImport(ImportedTemplate template, TemplateImportAction action,
                                    PositionTemplate target) {
}
