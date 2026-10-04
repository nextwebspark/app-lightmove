package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Adds the tags to every person named, or, with {@code remove}, takes them off. */
public record BulkTagPeopleRequest(
        @NotEmpty @Size(max = 500) List<@NotNull UUID> personIds,
        @NotEmpty @Size(max = 20) List<@NotNull UUID> tagIds,
        Boolean remove
) {

    public boolean removes() {
        return Boolean.TRUE.equals(remove);
    }
}
