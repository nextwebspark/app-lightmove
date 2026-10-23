package app.lightmove.api.notetaker.model;

import java.util.List;

/**
 * A call's summary as the model wrote it, every field what the transcript said. The four brief fields are empty for
 * a call about no position.
 */
public record MeetingSummary(String overview, List<String> keyPoints, String motivation, String packageSaid,
                             String notice, String mobility, List<String> concerns, List<String> nextSteps,
                             List<CompetencyEvidence> competencyEvidence, List<String> competenciesNotCovered,
                             List<String> briefConcerns) {}
