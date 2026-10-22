package app.lightmove.api.candidate.model;

import java.util.UUID;

/**
 * The Meeting note a recorded call left, and the position and mapping it was filed on — both null for a note about
 * the person, which is also where a call goes whose person was unmapped from its position since.
 */
public record FiledMeetingNote(UUID noteId, UUID projectId, UUID candidateId) {}
