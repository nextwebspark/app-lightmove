package app.lightmove.api.triagecompany.model;

import java.util.UUID;

/** One row of a grouped count: how many triaged companies one mandate holds. */
public record TriageCompanyCount(UUID projectId, long total) {}
