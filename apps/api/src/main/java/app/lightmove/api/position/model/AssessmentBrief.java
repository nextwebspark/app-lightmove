package app.lightmove.api.position.model;

import app.lightmove.api.common.constant.Seniority;
import java.util.List;

/**
 * What the AI reads of a brief to assess an executive against it: the role, why it exists, what it asks for, and
 * the weighted competencies. Never the compensation or the internal context, which have no bearing on a score.
 *
 * @param strategicPriorities the selected ones only
 */
public record AssessmentBrief(
        String roleTitle,
        Seniority seniority,
        String department,
        String locationCity,
        String locationCountry,
        String narrative,
        List<String> responsibilities,
        String businessDriver,
        List<String> strategicPriorities,
        List<BriefCriterion> criteria,
        BriefCompetencies competencies
) {}
