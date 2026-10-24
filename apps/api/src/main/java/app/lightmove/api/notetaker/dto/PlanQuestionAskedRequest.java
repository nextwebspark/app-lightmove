package app.lightmove.api.notetaker.dto;

/** A prepared question ticked as asked, or the tick taken back, by hand. */
public record PlanQuestionAskedRequest(boolean asked) {}
