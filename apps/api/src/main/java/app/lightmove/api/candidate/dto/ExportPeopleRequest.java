package app.lightmove.api.candidate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Exactly the people ticked, as the body of a POST: a long selection spelled into a query string would
 * be refused by the transport before the server ever read it.
 */
public record ExportPeopleRequest(@NotEmpty @Size(max = 10_000) List<@NotNull UUID> personIds) {}
