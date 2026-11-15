package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * The purposes to ask one person about, as {@code ConsentPurpose} wire tokens. A share names its position in
 * {@code projectId}; {@code nameClient} true names the client in the email.
 */
public record ConsentRequestRequest(@NotEmpty @Size(max = 3) List<@NotNull String> purposes, UUID projectId,
                                    Boolean nameClient) {}
