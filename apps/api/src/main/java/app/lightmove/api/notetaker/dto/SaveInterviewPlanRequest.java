package app.lightmove.api.notetaker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The whole plan as the consultant left it: every question in order, ticked or not. */
public record SaveInterviewPlanRequest(@NotNull @Size(max = 40) List<@Valid @NotNull InterviewQuestionDto> questions) {}
