package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.constant.ApproachKind;
import java.time.Instant;
import java.util.UUID;

/**
 * One approach as a screen names it: who, how, when and for which position. Never the words. On a position the
 * caller cannot open, {@code positionHidden} is set and the position's id and title are left out.
 */
public record ApproachTouchResponse(ApproachKind kind, UUID byUserId, String byName, Instant at, UUID projectId,
                                    String positionTitle, boolean samePosition, boolean byYou,
                                    boolean positionHidden) {}
