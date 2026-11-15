package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An answer the person gave another way, recorded by a colleague. {@code purpose} is a {@code ConsentPurpose} token,
 * {@code answer} {@code given}, {@code declined} or {@code withdrawn}, {@code answeredOn} today or earlier;
 * {@code projectId} names the position of a share.
 */
public record RecordConsentRequest(@NotBlank String purpose, UUID projectId, @NotBlank String answer,
                                   @NotNull LocalDate answeredOn) {}
