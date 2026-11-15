package app.lightmove.api.candidate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Every answer the person gives at once on their privacy page. */
public record ConsentAnswersRequest(@NotEmpty @Size(max = 10) List<@Valid ConsentAnswerRequest> answers) {}
