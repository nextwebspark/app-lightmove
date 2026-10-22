package app.lightmove.api.notetaker.model;

/** What a call showed against one of the brief's competencies, and when in the call it was said. */
public record CompetencyEvidence(String competency, String evidence, String at) {}
