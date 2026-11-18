package app.lightmove.api.notetaker.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What Suggest changes found on one recorded call. {@code asked} is false until the transcript was read for changes —
 * once its notes were filed, or by a press where that found nothing usable; {@code mayAskAgain} once every suggestion
 * is decided and the transcript is still kept; {@code suggesting} while a reading holds the claim, so a window waiting
 * on another's reading knows when it ended with nothing stored.
 */
public record CallSuggestionsResponse(UUID recordingId, String title, Instant startsAt, boolean asked,
                                      boolean mayAskAgain, boolean suggesting,
                                      List<CallSuggestionResponse> suggestions) {}
