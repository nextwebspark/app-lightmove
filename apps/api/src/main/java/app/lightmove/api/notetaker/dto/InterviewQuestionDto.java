package app.lightmove.api.notetaker.dto;

import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.notetaker.constant.InterviewQuestionSection;
import app.lightmove.api.notetaker.constant.InterviewQuestionSource;
import app.lightmove.api.notetaker.constant.RecordGap;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * One question of an interview plan, as read and as saved. On a save, {@code id} is the question's when it was read
 * (a new one sends null) and {@code source} is ignored: the server keeps whose a question is.
 */
public record InterviewQuestionDto(
        @Size(max = 64) String id,
        @NotNull InterviewQuestionSection section,
        CompetencyPanel panel,
        @Size(max = 200) String competency,
        RecordGap gap,
        @NotBlank @Size(max = 400) String text,
        @Size(max = 200) String listenFor,
        @Size(max = 200) String why,
        boolean included,
        InterviewQuestionSource source
) {}
