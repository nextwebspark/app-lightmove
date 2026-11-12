package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.AiUse;
import java.util.List;
import java.util.Set;

/** What one feature holds on the person: its sections, the files beside them, and which AI uses left anything. */
public record SubjectAccessContribution(List<SubjectAccessSection> sections, Set<AiUse> aiUses,
                                        List<SubjectAccessFile> files) {

    public SubjectAccessContribution(List<SubjectAccessSection> sections, Set<AiUse> aiUses) {
        this(sections, aiUses, List.of());
    }
}
