package app.lightmove.api.dashboard.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Every chart of a dashboard, answered together over one read of the data they share. */
public record ChartAnswersRequest(@NotNull @Size(max = 30) List<@Valid @NotNull ChartQuestionDto> charts) {}
