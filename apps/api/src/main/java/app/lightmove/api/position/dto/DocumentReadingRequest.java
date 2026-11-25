package app.lightmove.api.position.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Set;

/**
 * What a document reading set on one step, sent with the write its fill schedules (#1452): the scalars it left as
 * the document's and, per list, how many rows. Taken from the fill, never from the write, so an edit made before the
 * write goes out still counts as changed. {@code version} null: no prompt version answered it.
 */
public record DocumentReadingRequest(
        @Positive Integer version,
        @Size(max = 12) Set<@NotBlank @Size(max = 64) String> fields,
        @Size(max = 6) Map<@NotBlank @Size(max = 64) String, @NotNull @Positive @Max(60) Integer> listItems
) {

    public DocumentReadingRequest {
        fields = fields == null ? Set.of() : fields;
        listItems = listItems == null ? Map.of() : listItems;
    }
}
