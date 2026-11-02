package app.lightmove.api.retention.dto;

/**
 * What a delete did: {@code erased}; {@code noLongerDue}, touched, mapped or restricted since the review (or gone);
 * {@code sendInFlight}, an email to them still going out, so they wait for the next review.
 */
public record RetentionEraseResponse(int erased, int noLongerDue, int sendInFlight) {}
