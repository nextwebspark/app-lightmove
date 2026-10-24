package app.lightmove.api.notetaker.model;

/**
 * What a call showed against one of the brief's competencies and when it was said, with the executive's own words
 * where a quote was found verbatim in the transcript, and whether they backed it with an example ({@code null} where
 * the model did not say).
 */
public record CompetencyEvidence(String competency, String evidence, String at, String quote, Boolean example) {}
