package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The purposes to ask one person about, as {@code ConsentPurpose} wire tokens. */
public record ConsentRequestRequest(@NotEmpty @Size(max = 3) List<@NotNull String> purposes) {}
