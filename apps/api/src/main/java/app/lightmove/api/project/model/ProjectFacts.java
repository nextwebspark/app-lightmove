package app.lightmove.api.project.model;

import app.lightmove.api.project.constant.ProjectStage;
import app.lightmove.api.project.constant.ProjectType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A mandate reduced to what somebody outside {@code project} needs to name it.
 *
 * <p>Narrower than {@code ProjectResponse} on purpose, and not a DTO: that record assembles the
 * team, the attached representatives and two counted queries, which is a screen's worth of work for
 * a caller that wants to say which search it is looking at.
 *
 * @param clientName the hiring company, which is usually what a question is actually about
 * @param shortlistTargetDate null on a mapping-only mandate, which owes no shortlist
 */
public record ProjectFacts(UUID id, String positionTitle, String clientName, ProjectStage stage,
                           ProjectType projectType, LocalDate mappingTargetDate,
                           LocalDate shortlistTargetDate) {
}
