package app.lightmove.api.companydiscovery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A market question, and the little that constrains it.
 *
 * <p><b>Deliberately nothing shaped like the saved Apollo filter.</b> Bands, sector groups and market
 * segments are the universe's own vocabulary, chosen so a SQL predicate can be built from them; a
 * question put to a web search is a sentence. Offering both on one form would invite a consultant to
 * fill in half of each and get an answer that honoured neither.
 *
 * <p>{@code projectId} is optional and is only ever used to say which of the answered companies this
 * mandate already holds. It is authorised separately, against {@code WORK_VIEW}, before it is used
 * for anything — naming it here buys the caller nothing they did not already have.
 */
public record DiscoverCompaniesRequest(
        @NotBlank @Size(max = 2000) String question,
        @Size(max = 100) String country,
        Integer limit,
        UUID projectId) {}
