package app.lightmove.api.positiontemplate.model;

import java.util.List;

/**
 * One template as read from an import file: the code it will be filed under, and either a draft ready
 * to write or the problems that stop it. {@code draft} is null whenever {@code problems} is not empty.
 */
public record ImportedTemplate(String code, String title, PositionTemplateDraft draft,
                               List<TemplateProblem> problems) {

    public ImportedTemplate {
        problems = List.copyOf(problems);
    }

    public boolean isValid() {
        return problems.isEmpty();
    }
}
