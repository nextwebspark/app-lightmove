package app.lightmove.api.retention.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** The people ticked under Due for deletion, to keep or to delete. */
public record RetentionPeopleRequest(@NotEmpty @Size(max = 200) List<@NotNull UUID> personIds) {}
