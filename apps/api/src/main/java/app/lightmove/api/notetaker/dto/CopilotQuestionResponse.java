package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.InterviewQuestionSection;

/** A prepared question the consultant kept ticked, and whether it has been asked. */
public record CopilotQuestionResponse(String id, InterviewQuestionSection section, String competency, String text,
                                      boolean asked) {}
