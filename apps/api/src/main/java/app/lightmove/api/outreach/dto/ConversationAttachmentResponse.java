package app.lightmove.api.outreach.dto;

import java.util.UUID;

/** A file an email in the conversation carried: what to call it, and its size. The bytes are a download of their own. */
public record ConversationAttachmentResponse(UUID id, String fileName, String contentType, long sizeBytes) {}
