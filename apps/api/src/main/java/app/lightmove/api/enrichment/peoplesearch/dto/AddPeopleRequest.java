package app.lightmove.api.enrichment.peoplesearch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The people ticked on a page, by LinkedIn slug; a page holds twenty-five, so a request does too.
 * {@code status} is the stage their employers are filed at — {@code inUniverse} when absent.
 */
public record AddPeopleRequest(
        @NotNull(message = "Tick at least one person")
        @Size(min = 1, max = 25, message = "Between one and twenty-five people at a time")
        List<@NotBlank @Size(max = 200) String> linkedinSlugs,
        String status
) {}
