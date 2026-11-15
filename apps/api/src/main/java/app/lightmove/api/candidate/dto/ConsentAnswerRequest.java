package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** One answer on the privacy page: {@code given} or {@code declined} to a request, {@code withdrawn} from a consent. */
public record ConsentAnswerRequest(@NotNull UUID consentId, @NotBlank String answer) {}
