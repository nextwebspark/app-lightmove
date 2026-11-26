package app.lightmove.api.subprocessor.model;

import java.util.UUID;

/** Published with the notice's insert, so its emails go only once the notice is committed. */
public record SubprocessorNoticePublished(UUID noticeId) {
}
