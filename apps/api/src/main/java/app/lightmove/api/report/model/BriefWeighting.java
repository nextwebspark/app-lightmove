package app.lightmove.api.report.model;

import java.util.List;

/** The brief's technical share and competency weights at a scenario's save: what the screen says changed since. */
public record BriefWeighting(int technicalShare, List<CompetencyWeight> competencies) {}
