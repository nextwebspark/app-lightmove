package app.lightmove.api.dashboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

/** One chart a dashboard asks to have answered: its library query and options. */
public record ChartQuestionDto(@NotBlank @Size(max = 64) String queryId, @Size(max = 8) Map<String, String> options) {}
