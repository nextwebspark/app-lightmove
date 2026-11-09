package app.lightmove.api.retention.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * The people ticked under Due for deletion, to delete. Fewer than a keep takes: each is a whole erasure, and the
 * screen sends a longer selection in batches so no one request outlives a gateway's timeout.
 */
public record RetentionEraseRequest(@NotEmpty @Size(max = 50) List<@NotNull UUID> personIds) {}
