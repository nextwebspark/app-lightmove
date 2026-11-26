package app.lightmove.api.subprocessor.model;

import java.time.LocalDate;

/** What a super admin publishes, already checked and trimmed. */
public record SubprocessorNoticeDraft(String provider, String receives, String purpose, String region,
                                      LocalDate effectiveOn, String listUrl) {
}
